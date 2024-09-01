package org.homio.app.manager.common.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.ContextBGP;
import org.homio.api.ContextNetwork;
import org.homio.api.model.Icon;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.hquery.Curl;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.network.NetworkHardwareRepository.CidrAddress;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;

import javax.jmdns.ServiceInfo;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

@Log4j2
@RequiredArgsConstructor
public class ContextNetworkImpl implements ContextNetwork {

    private final ContextImpl context;
    private final MachineHardwareRepository machineHardwareRepository;
    private final NetworkHardwareRepository networkHardwareRepository;

    private final MDNSClient mdnsClient = new MDNSClient();
    private final Map<String, BiConsumer<List<CidrAddress>, List<CidrAddress>>> networkAddressChangeListeners = new HashMap<>();
    private Collection<CidrAddress> lastKnownInterfaceAddresses;
    private final Map<String, UdpContext> listenUdpMap = new HashMap<>();

    @Override
    public @NotNull String getHostname() {
        return machineHardwareRepository.getMachineInfo().getNetworkNodeHostname();
    }

    @Override
    public @NotNull List<ServiceInfo> scanMDNS(@NotNull String serviceType) {
        return mdnsClient.list(serviceType, Duration.ofSeconds(6));
    }

    @Override
    public void addNetworkAddressChanged(@NotNull String key, @NotNull BiConsumer<List<CidrAddress>, List<CidrAddress>> listener) {
        networkAddressChangeListeners.put(key, listener);
    }

    @Override
    public void removeNetworkAddressChanged(@NotNull String key) {
        networkAddressChangeListeners.remove(key);
    }

    private static final Map<String, Object> dataCache = new ConcurrentHashMap<>();

    @Override
    public <T> T fetchCached(String address, Class<T> typeClass) {
        return (T) dataCache.computeIfAbsent(address, s -> Curl.get(address, typeClass));
    }

    public void onContextCreated() {
        addNetworkAddressChanged("mdnsClient", (inetAddress, inetAddress2) -> {
            mdnsClient.close();
            mdnsClient.start();
        });
        mdnsClient.start();
        context.bgp().builder("network-interface-poll")
                .delay(Duration.ofSeconds(1))
                .interval(Duration.ofSeconds(60)).execute(this::pollAndNotifyNetworkInterfaceAddress);
    }

    private void pollAndNotifyNetworkInterfaceAddress() {
        fetchIpAddress();

        Collection<CidrAddress> newInterfaceAddresses = NetworkHardwareRepository.getAllInterfaceAddresses();
        if (lastKnownInterfaceAddresses == null) {
            lastKnownInterfaceAddresses = newInterfaceAddresses;
            return;
        }
        // Look for added addresses to notify
        List<CidrAddress> added = newInterfaceAddresses
                .stream().filter(newInterfaceAddr -> !lastKnownInterfaceAddresses.contains(newInterfaceAddr)).toList();

        // Look for removed addresses to notify
        List<CidrAddress> removed = lastKnownInterfaceAddresses
                .stream().filter(lastKnownInterfaceAddr -> !newInterfaceAddresses.contains(lastKnownInterfaceAddr)).toList();

        lastKnownInterfaceAddresses = newInterfaceAddresses;

        if (!added.isEmpty() || !removed.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("added {} network interfaces: {}", added.size(), Arrays.deepToString(added.toArray()));
                log.debug("removed {} network interfaces: {}", removed.size(), Arrays.deepToString(removed.toArray()));
            }

            notifyUpAddressChangeListeners(added, removed);
        }
    }

    private void fetchIpAddress() {
        MACHINE_IP_ADDRESS = networkHardwareRepository.getIPAddress();
        if (SystemUtils.IS_OS_LINUX) {
            if (MACHINE_IP_ADDRESS.startsWith("169.254")) {
                try {
                    log.warn("Device ip address is: {}. Trying to call autohotspot",
                            machineHardwareRepository.execute("/usr/bin/autohotspot", 60));
                } catch (Exception ex) {
                    log.warn("Device ip address is: {}. Unable to run autohotspot to fix: {}",
                            MACHINE_IP_ADDRESS, CommonUtils.getErrorMessage(ex));
                }
            }
        }
    }

    private void notifyUpAddressChangeListeners(List<CidrAddress> added, List<CidrAddress> removed) {
        List<CidrAddress> unmodifiableAddedList = Collections.unmodifiableList(added);
        List<CidrAddress> unmodifiableRemovedList = Collections.unmodifiableList(removed);
        context.bgp().execute(() -> {
            for (BiConsumer<List<CidrAddress>, List<CidrAddress>> listener : networkAddressChangeListeners.values()) {
                listener.accept(unmodifiableAddedList, unmodifiableRemovedList);
            }
        });
    }

    @Override
    @SneakyThrows
    public void listenUdp(
            String key, String host, int port, BiConsumer<DatagramPacket, String> listener) {
        String hostPortKey = (host == null ? "0.0.0.0" : host) + ":" + port;
        if (!this.listenUdpMap.containsKey(hostPortKey)) {
            ContextBGP.ThreadContext<Void> scheduleFuture;
            try {
                DatagramSocket socket = new DatagramSocket(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
                DatagramPacket datagramPacket = new DatagramPacket(new byte[255], 255);

                scheduleFuture = context.bgp().builder("listen-udp-" + hostPortKey).execute(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        socket.receive(datagramPacket);
                        byte[] data = datagramPacket.getData();
                        String text = new String(data, 0, datagramPacket.getLength());
                        listenUdpMap.get(hostPortKey).handle(datagramPacket, text);
                    }
                });
                scheduleFuture.setDescription("Listen udp: " + hostPortKey);
            } catch (Exception ex) {
                context.ui().notification().addOrUpdateBlock("UPD", "UDP", new Icon("fas fa-kip-sign", "#482594"), blockBuilder -> {
                    String info = Lang.getServerMessage("UDP_ERROR", FlowMap.of("key", hostPortKey, "msg", ex.getMessage()));
                    blockBuilder.addInfo(info, new Icon("fas fa-triangle-exclamation"));
                });
                log.error("Unable to listen udp host:port: <{}>", hostPortKey);
                return;
            }
            this.listenUdpMap.put(hostPortKey, new UdpContext(scheduleFuture));
        }
        this.listenUdpMap.get(hostPortKey).put(key, listener);
    }

    public void stopListenUdp(String key) {
        for (UdpContext udpContext : this.listenUdpMap.values()) {
            udpContext.cancel(key);
        }
    }

    @RequiredArgsConstructor
    private static class UdpContext {

        private final Map<String, BiConsumer<DatagramPacket, String>> keyToListener = new HashMap<>();
        private final ContextBGP.ThreadContext<Void> scheduleFuture;

        public void handle(DatagramPacket datagramPacket, String text) {
            for (BiConsumer<DatagramPacket, String> listener : keyToListener.values()) {
                listener.accept(datagramPacket, text);
            }
        }

        public void put(String key, BiConsumer<DatagramPacket, String> listener) {
            this.keyToListener.put(key, listener);
        }

        public void cancel(String key) {
            keyToListener.remove(key);
            if (keyToListener.isEmpty()) {
                scheduleFuture.cancel();
            }
        }
    }
}

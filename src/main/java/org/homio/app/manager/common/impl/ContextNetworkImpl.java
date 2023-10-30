package org.homio.app.manager.common.impl;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.jmdns.ServiceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.ContextNetwork;
import org.homio.app.manager.common.ContextImpl;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.network.NetworkHardwareRepository.CidrAddress;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;

@Log4j2
@RequiredArgsConstructor
public class ContextNetworkImpl implements ContextNetwork {

    private final ContextImpl context;
    private final MachineHardwareRepository hardwareRepository;

    private final MDNSClient mdnsClient = new MDNSClient();
    private final Map<String, BiConsumer<List<CidrAddress>, List<CidrAddress>>> networkAddressChangeListeners = new HashMap<>();
    private Collection<CidrAddress> lastKnownInterfaceAddresses;

    @Override
    public @NotNull String getHostname() {
        return hardwareRepository.getMachineInfo().getNetworkNodeHostname();
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

            notifyListeners(added, removed);
        }
    }

    private void notifyListeners(List<CidrAddress> added, List<CidrAddress> removed) {
        List<CidrAddress> unmodifiableAddedList = Collections.unmodifiableList(added);
        List<CidrAddress> unmodifiableRemovedList = Collections.unmodifiableList(removed);
        context.bgp().execute(() -> {
            for (BiConsumer<List<CidrAddress>, List<CidrAddress>> listener : networkAddressChangeListeners.values()) {
                listener.accept(unmodifiableAddedList, unmodifiableRemovedList);
            }
        });
    }
}

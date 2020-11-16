package org.touchhome.app.manager.common.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.EntityContextUDP;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.util.FlowMap;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUDPImpl implements EntityContextUDP {

    private final EntityContext entityContext;
    private final Map<String, UdpContext> listenUdpMap = new HashMap<>();

    @Override
    @SneakyThrows
    public void listenUdp(String key, String host, int port, BiConsumer<DatagramPacket, String> listener) {
        String hostPortKey = (host == null ? "0.0.0.0" : host) + ":" + port;
        if (!this.listenUdpMap.containsKey(hostPortKey)) {
            EntityContextBGP.ThreadContext<Void> scheduleFuture;
            try {
                DatagramSocket socket = new DatagramSocket(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
                DatagramPacket datagramPacket = new DatagramPacket(new byte[255], 255);

                scheduleFuture = entityContext.bgp().schedule("listen-udp-" + hostPortKey, 1, TimeUnit.SECONDS, () -> {
                    socket.receive(datagramPacket);
                    byte[] data = datagramPacket.getData();
                    String text = new String(data, 0, datagramPacket.getLength());
                    listenUdpMap.get(hostPortKey).handle(datagramPacket, text);
                }, true);
                scheduleFuture.setDescription("Listen udp: " + hostPortKey);
            } catch (Exception ex) {
                entityContext.ui().addHeaderErrorNotification(hostPortKey, "UDP " + hostPortKey, En.getServerMessage("UDP_ERROR",
                        FlowMap.of("key", hostPortKey, "msg", ex.getMessage())));
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
        private final EntityContextBGP.ThreadContext<Void> scheduleFuture;

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

package org.homio.app.manager.common.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import lombok.extern.log4j.Log4j2;
import org.homio.api.util.HardwareUtils;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class MDNSClient {

    private final Map<InetAddress, JmDNS> jmdnsInstances = new ConcurrentHashMap<>();

    private Set<InetAddress> getAllInetAddresses() {
        final Set<InetAddress> addresses = new HashSet<>();
        Enumeration<NetworkInterface> itInterfaces;
        try {
            itInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (final SocketException e) {
            return addresses;
        }
        while (itInterfaces.hasMoreElements()) {
            final NetworkInterface iface = itInterfaces.nextElement();
            try {
                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }
            } catch (final SocketException ex) {
                continue;
            }

            Enumeration<InetAddress> itAddresses = iface.getInetAddresses();
            while (itAddresses.hasMoreElements()) {
                final InetAddress address = itAddresses.nextElement();
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                    continue;
                }
                addresses.add(address);
            }
        }
        return addresses;
    }


    public Set<JmDNS> getClientInstances() {
        return new HashSet<>(jmdnsInstances.values());
    }

    public void start() {
        for (InetAddress address : getAllInetAddresses()) {
            createJmDNSByAddress(address);
        }

        Set<ServiceDescription> services = Set.of(
            new ServiceDescription("_homio-server._tcp.local.", "homio", 9111, Map.of(
                "id", HardwareUtils.APP_ID,
                "run", String.valueOf(HardwareUtils.RUN_COUNT),
                "version", System.getProperty("server.version"))));
        registerService(services);
    }

    private void registerService(Set<ServiceDescription> services) {
        for (ServiceDescription service : services) {
            try {
                registerServiceInternal(service);
            } catch (IOException e) {
                log.warn("Exception while registering service", e);
            }
        }
    }

    public void addServiceListener(String type, ServiceListener listener) {
        jmdnsInstances.values().forEach(jmdns -> jmdns.addServiceListener(type, listener));
    }


    public void removeServiceListener(String type, ServiceListener listener) {
        jmdnsInstances.values().forEach(jmdns -> jmdns.removeServiceListener(type, listener));
    }

    private void registerServiceInternal(ServiceDescription service) throws IOException {
        for (JmDNS instance : jmdnsInstances.values()) {
            log.info("Registering new service {} at {}:{} ({})", service.serviceType,
                instance.getInetAddress().getHostAddress(), service.port, instance.getName());
            // Create one ServiceInfo object for each JmDNS instance
            ServiceInfo serviceInfo = ServiceInfo.create(service.serviceType, service.serviceName, service.port,
                0, 0, service.properties);
            instance.registerService(serviceInfo);
        }
    }

    public void unregisterAllServices() {
        for (JmDNS instance : jmdnsInstances.values()) {
            instance.unregisterAllServices();
        }
    }

    public @NotNull List<ServiceInfo> list(@NotNull String type) {
        List<ServiceInfo> services = new ArrayList<>();
        for (JmDNS instance : jmdnsInstances.values()) {
            for (ServiceInfo serviceInfo : instance.list(type)) {
                services.add(serviceInfo);
            }
        }
        return services;
    }

    public @NotNull List<ServiceInfo> list(@NotNull String type, @NotNull Duration timeout) {
        List<ServiceInfo> services = new ArrayList<>();
        for (JmDNS instance : jmdnsInstances.values()) {
            for (ServiceInfo serviceInfo : instance.list(type, timeout.toMillis())) {
                services.add(serviceInfo);
            }
        }
        return services;
    }

    public void close() {
        for (JmDNS jmdns : jmdnsInstances.values()) {
            closeQuietly(jmdns);
            log.debug("mDNS service has been stopped ({})", jmdns.getName());
        }
        jmdnsInstances.clear();
    }

    private void closeQuietly(JmDNS jmdns) {
        try {
            jmdns.close();
        } catch (IOException e) {
        }
    }

    private void createJmDNSByAddress(InetAddress address) {
        try {
            JmDNS jmdns = JmDNS.create(address, null);
            jmdnsInstances.put(address, jmdns);
            log.debug("mDNS service has been started ({} for IP {})", jmdns.getName(), address.getHostAddress());
        } catch (IOException e) {
            log.debug("JmDNS instantiation failed ({})!", address.getHostAddress());
        }
    }

    private record ServiceDescription(String serviceType, String serviceName, int port, Map<String, String> properties) {}
}

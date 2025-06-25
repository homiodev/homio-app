package org.homio.app.console;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.jmdns.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class MDNSNetworkConsolePlugin implements ConsolePluginTable<MDNSNetworkConsolePlugin.MdnsServiceEntity> {

    private static final long CACHE_DURATION_MS = 60 * 1000;
    private static final long TXT_REFRESH_INTERVAL_SECONDS = 30;
    @Getter
    private final @Accessors(fluent = true) Context context;
    private final List<MdnsServiceEntity> liveDiscoveredServices = new CopyOnWriteArrayList<>();
    private final Map<String, ServiceInfo> liveServiceInfoCache = new ConcurrentHashMap<>();
    private final Set<String> discoveredServiceTypes = new CopyOnWriteArraySet<>();
    private final AtomicReference<List<MdnsServiceEntity>> cachedDisplayList = new AtomicReference<>(Collections.emptyList());
    private final ReentrantLock cacheUpdateLock = new ReentrantLock();
    private JmDNS jmdns;
    private ServiceListener serviceListener;
    private volatile long lastCacheUpdateTimeMillis = 0;
    private ScheduledExecutorService periodicServiceRefresherScheduler;

    @Override
    public String getParentTab() {
        return "hardware";
    }

    @Override
    @SneakyThrows
    public Collection<MdnsServiceEntity> getValue() {
        initializeJmDNSFramework();

        long currentTimeMillis = System.currentTimeMillis();
        if ((currentTimeMillis - lastCacheUpdateTimeMillis > CACHE_DURATION_MS) || cachedDisplayList.get().isEmpty()) {
            if (cacheUpdateLock.tryLock()) {
                try {
                    if ((currentTimeMillis - lastCacheUpdateTimeMillis > CACHE_DURATION_MS) || cachedDisplayList.get().isEmpty()) {
                        List<MdnsServiceEntity> snapshot = new ArrayList<>(liveDiscoveredServices);
                        Collections.sort(snapshot);
                        cachedDisplayList.set(Collections.unmodifiableList(snapshot));
                        lastCacheUpdateTimeMillis = currentTimeMillis;
                    }
                } finally {
                    cacheUpdateLock.unlock();
                }
            }
        }
        return cachedDisplayList.get();
    }

    @SneakyThrows
    private synchronized void initializeJmDNSFramework() {
        if (jmdns != null) {
            return;
        }

        try {
            jmdns = context.network().getPrimaryMDNS(null);

            serviceListener = new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    String uniqueId = event.getInfo() != null ? event.getInfo().getQualifiedName() : event.getType() + "::" + event.getName();
                    liveDiscoveredServices.removeIf(s -> s.getEntityID().equals(uniqueId));
                    liveServiceInfoCache.remove(uniqueId);
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo info = event.getInfo();
                    if (info != null) {
                        String uniqueId = info.getQualifiedName();
                        liveServiceInfoCache.put(uniqueId, info);
                        liveDiscoveredServices.removeIf(s -> s.getEntityID().equals(uniqueId));
                        liveDiscoveredServices.add(new MdnsServiceEntity(info));
                    }
                }
            };

            jmdns.addServiceTypeListener(new ServiceTypeListener() {
                @Override
                public void serviceTypeAdded(ServiceEvent event) {
                    String serviceType = event.getType();
                    if (!serviceType.endsWith(".")) serviceType += ".";
                    if (discoveredServiceTypes.add(serviceType)) {
                        jmdns.addServiceListener(serviceType, serviceListener);
                    }
                }

                @Override
                public void subTypeForServiceTypeAdded(ServiceEvent event) {
                }
            });

            startPeriodicServiceRefresher();

        } catch (IOException e) {
            jmdns = null;
            throw e;
        }
    }

    private void startPeriodicServiceRefresher() {
        if (this.periodicServiceRefresherScheduler == null || this.periodicServiceRefresherScheduler.isShutdown()) {
            this.periodicServiceRefresherScheduler = Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "mdns-txt-refresher");
                        thread.setDaemon(true);
                        return thread;
                    });

            periodicServiceRefresherScheduler.scheduleAtFixedRate(this::refreshKnownServicesTXTs,
                    TXT_REFRESH_INTERVAL_SECONDS,
                    TXT_REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    private void refreshKnownServicesTXTs() {
        if (jmdns == null || liveServiceInfoCache.isEmpty()) {
            return;
        }
        List<ServiceInfo> servicesToRefresh = new ArrayList<>(liveServiceInfoCache.values());
        for (ServiceInfo cachedInfo : servicesToRefresh) {
            if (jmdns != null) {
                jmdns.requestServiceInfo(cachedInfo.getType(), cachedInfo.getName(), 500);
            } else {
                break;
            }
        }
    }

    @Override
    public int order() {
        return 1600;
    }

    @Override
    public @NotNull String getName() {
        return "mdns";
    }

    @Override
    public @NotNull Class<MdnsServiceEntity> getEntityClass() {
        return MdnsServiceEntity.class;
    }

    public void close() {
        if (periodicServiceRefresherScheduler != null) {
            periodicServiceRefresherScheduler.shutdownNow();
            periodicServiceRefresherScheduler = null;
        }
        if (jmdns != null) {
            try {
                jmdns.close();
            } catch (IOException ignored) {
            }
            jmdns = null;
        }
        liveDiscoveredServices.clear();
        liveServiceInfoCache.clear();
        discoveredServiceTypes.clear();
        cachedDisplayList.set(Collections.emptyList());
        lastCacheUpdateTimeMillis = 0;
    }

    @Getter
    @NoArgsConstructor
    public static class MdnsServiceEntity implements HasEntityIdentifier, Comparable<MdnsServiceEntity> {

        private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        @UIField(order = 1)
        private String serviceName;
        @UIField(order = 2)
        private String serviceType;
        @UIField(order = 3)
        private String applicationProtocol;
        @UIField(order = 4)
        private int port;
        @UIField(order = 5)
        private String hostAddresses;
        @UIField(order = 6)
        private String serverOrInstance;
        @UIField(order = 7, fullWidth = true)
        private String txtRecordsString;
        @Getter
        private String entityID;


        public MdnsServiceEntity(ServiceInfo serviceInfo) {
            this.serviceName = serviceInfo.getName();
            this.serviceType = serviceInfo.getType();

            if (this.serviceType != null && this.serviceType.startsWith("_")) {
                this.applicationProtocol = this.serviceType.substring(1).split("\\.")[0];
            } else {
                this.applicationProtocol = "N/A";
            }

            this.port = serviceInfo.getPort();

            List<String> addresses = new ArrayList<>();
            for (InetAddress addr : serviceInfo.getInet4Addresses()) {
                addresses.add(addr.getHostAddress() + " (IPv4)");
            }
            for (InetAddress addr : serviceInfo.getInet6Addresses()) {
                addresses.add(addr.getHostAddress() + " (IPv6)");
            }
            this.hostAddresses = addresses.isEmpty() ? "N/A" : String.join("<br>", addresses);

            this.serverOrInstance = serviceInfo.getServer();
            this.entityID = serviceInfo.getQualifiedName();

            StringBuilder txtBuilder = new StringBuilder();
            List<String> propertyNames = Collections.list(serviceInfo.getPropertyNames());
            Collections.sort(propertyNames);

            for (String key : propertyNames) {
                if (!txtBuilder.isEmpty()) {
                    txtBuilder.append("\n");
                }
                byte[] valueBytes = serviceInfo.getPropertyBytes(key);
                String valueString;
                if (isPrintable(valueBytes)) {
                    valueString = new String(valueBytes);
                } else {
                    valueString = "[binary: " + bytesToHex(valueBytes) + "]";
                }
                txtBuilder.append(key).append("=").append(valueString);
            }
            this.txtRecordsString = txtBuilder.toString();
            if (this.txtRecordsString.isEmpty()) {
                this.txtRecordsString = "N/A";
            }
        }

        private static boolean isPrintable(byte[] bytes) {
            if (bytes == null) return false;
            for (byte b : bytes) {
                if (b < 32 || b > 126) {
                    return false;
                }
            }
            return true;
        }

        public static String bytesToHex(byte[] bytes) {
            if (bytes == null) return "";
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

        @Override
        public int compareTo(@NotNull MdnsServiceEntity o) {
            return this.entityID.compareTo(o.entityID);
        }
    }
}
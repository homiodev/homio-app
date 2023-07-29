package org.homio.addon.tuya.service;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.addon.tuya.TuyaDeviceEndpoint;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.addon.tuya.internal.local.DeviceInfoSubscriber;
import org.homio.addon.tuya.internal.local.DeviceStatusListener;
import org.homio.addon.tuya.internal.local.TuyaDeviceCommunicator;
import org.homio.addon.tuya.internal.local.UdpDiscoveryListener;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.tuya.service.TuyaDiscoveryService.updateTuyaDeviceEntity;

/**
 * The {@link TuyaDeviceService} handles commands and state updates
 */
@Log4j2
public class TuyaDeviceService extends ServiceInstance<TuyaDeviceEntity> implements DeviceInfoSubscriber, DeviceStatusListener {

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
            new ConfigDeviceDefinitionService("tuya-devices.json");

    private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private static final UdpDiscoveryListener udpDiscoveryListener = new UdpDiscoveryListener(eventLoopGroup);

    private @Nullable TuyaDeviceCommunicator tuyaDeviceCommunicator;

    private final AtomicBoolean disposing = new AtomicBoolean(false);

    private final Map<Integer, Pair<Long, Object>> statusCache = new ConcurrentHashMap<>();

    @Getter
    private final @NotNull Map<String, TuyaDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private @Nullable ThreadContext<Void> pollingJob;
    private @Nullable ThreadContext<Void> reconnectFuture;
    private @NotNull Map<String, SchemaDp> schemaDps = Map.of();
    private List<ConfigDeviceDefinition> models;

    public TuyaDeviceService(EntityContext entityContext, TuyaDeviceEntity entity) {
        super(entityContext, entity);
        this.initialize();
    }

    @Override
    public void processDeviceStatus(Map<Integer, Object> deviceStatus) {
        log.debug("[{}]: received status message '{}'", entity.getEntityID(), deviceStatus);

        if (deviceStatus.isEmpty()) {
            // if status is empty -> need to use control method to request device status
            Map<Integer, @Nullable Object> commandRequest = new HashMap<>();
            endpoints.values().forEach(p -> commandRequest.put(p.getDp(), null));
            endpoints.values().stream().filter(p -> p.getDp2() != -1).forEach(p -> commandRequest.put(p.getDp2(), null));

            TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
            if (tuyaDeviceCommunicator != null) {
                tuyaDeviceCommunicator.set(commandRequest);
            }
        } else {
            // addSingleExpiringCache
            for (Entry<Integer, Object> entry : deviceStatus.entrySet()) {
                statusCache.put(entry.getKey(), Pair.of(System.currentTimeMillis(), entry.getValue()));
            }
            deviceStatus.forEach(this::processEndpointStatus);
        }
    }

    @Override
    public void destroy() throws Exception {
        disposing.set(true);
        if (EntityContextBGP.cancel(reconnectFuture)) {
            reconnectFuture = null;
        }
        if (EntityContextBGP.cancel(pollingJob)) {
            pollingJob = null;
        }
        if (entity.getIp().isEmpty()) {
            // unregister listener only if IP is not fixed
            udpDiscoveryListener.unregisterListener(this);
        }
        TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
        if (tuyaDeviceCommunicator != null) {
            tuyaDeviceCommunicator.dispose();
            this.tuyaDeviceCommunicator = null;
        }
        disposing.set(false);
    }

    @Override
    @SneakyThrows
    public void initialize() {
        this.destroy(); // stop all before initialize
        try {
            if (StringUtils.isEmpty(entity.getIeeeAddress())) {
                throw new IllegalArgumentException("Empty device id");
            }
            if (isRequireFetchDeviceInfoFromCloud()) {
                entityContext.event().runOnceOnInternetUp("tuya-service-init-" + entity.getEntityID(), this::fetchDeviceInfo);
                return;
            }
            // check if we have endpoints and add them if available
            if (endpoints.isEmpty()) {
                this.schemaDps = entity.getSchema();
                if (!schemaDps.isEmpty()) {
                    // fallback to retrieved schema
                    for (Entry<String, SchemaDp> entry : schemaDps.entrySet()) {
                        endpoints.put(entry.getKey(), new TuyaDeviceEndpoint(entry.getValue(), entityContext, entity));
                    }
                } else {
                    entity.setStatus(Status.OFFLINE, "No endpoints found");
                    return;
                }
            }

            if (!entity.getIp().isBlank()) {
                deviceInfoChanged(new DeviceInfo(entity.getIp(), entity.getProtocolVersion().getVersionString()));
            } else {
                entity.setStatus(Status.WAITING, "Waiting for IP address");
                udpDiscoveryListener.registerListener(entity.getIeeeAddress(), this);
            }

            disposing.set(false);
        } catch (Exception ex) {
            entity.setStatusError(ex);
            log.error("[{}]: Error during initialize tuya device: {}", entity.getEntityID(), CommonUtils.getErrorMessage(ex));
        }
    }

    private void fetchDeviceInfo() {
        entity.setStatus(Status.INITIALIZE);
        // delay to able Tuya api get project
        entityContext.bgp().builder("tuya-init-" + entity.getEntityID())
                .delay(Duration.ofSeconds(1))
                .onError(e -> entity.setStatus(Status.ERROR, CommonUtils.getErrorMessage(e)))
                .execute(this::tryFetchDeviceInfo);
    }

    private boolean isRequireFetchDeviceInfoFromCloud() {
        return isNotEmpty(entity.getIeeeAddress()) &&
                (isEmpty(entity.getLocalKey()) || !entity.getJsonData().has("schema"));
    }

    @Override
    protected long getEntityHashCode(TuyaDeviceEntity entity) {
        return entity.getDeepHashCode();
    }

    @Override
    public void connectionStatus(boolean status) {
        if (status) {
            entity.setStatus(Status.ONLINE);
            int pollingInterval = entity.getPollingInterval();
            TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
            if (tuyaDeviceCommunicator != null && pollingInterval > 0) {
                pollingJob = entityContext.bgp().builder("tuya-device-pull-%s".formatted(entity.getEntityID()))
                                          .intervalWithDelay(Duration.ofSeconds(pollingInterval))
                                          .execute(tuyaDeviceCommunicator::refreshStatus);
            }
        } else {
            entity.setStatus(Status.OFFLINE);
            if (EntityContextBGP.cancel(pollingJob)) {
                pollingJob = null;
            }
            TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
            ThreadContext<Void> reconnectFuture = this.reconnectFuture;
            // only re-connect if a device is present, we are not disposing the thing and either the reconnectFuture is
            // empty or already done
            if (tuyaDeviceCommunicator != null && !disposing.get() && (reconnectFuture == null || reconnectFuture.isStopped())) {
                this.reconnectFuture = entityContext.bgp().builder("tuya-device-connect-%s".formatted(entity.getEntityID()))
                        .delay(Duration.ofSeconds(5))
                        .execute(tuyaDeviceCommunicator::connect);
            }
        }
    }

    @Override
    public void deviceInfoChanged(DeviceInfo deviceInfo) {
        log.info("[{}]: Configuring IP address '{}' for thing '{}'.", entity.getEntityID(), deviceInfo, entity.getTitle());

        TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
        if (tuyaDeviceCommunicator != null) {
            tuyaDeviceCommunicator.dispose();
        }
        entity.setStatus(Status.UNKNOWN);

        this.tuyaDeviceCommunicator = new TuyaDeviceCommunicator(this, eventLoopGroup,
            deviceInfo.ip(), deviceInfo.protocolVersion(), entity);
    }

    @SneakyThrows
    public void tryFetchDeviceInfo() {
        log.info("[{}]: Fetching device {} info", entity.getEntityID(), entity);
        TuyaOpenAPI api = entityContext.getBean(TuyaOpenAPI.class);
        entity.setStatus(Status.INITIALIZE);
        try {
            TuyaDeviceDTO tuyaDevice = api.getDevice(entity.getIeeeAddress(), entity);
            log.info("[{}]: Fetched device {} info successfully", entity.getEntityID(), entity);
            updateTuyaDeviceEntity(tuyaDevice, api, entity);
            entityContext.save(entity);

        } catch (Exception ex) {
            log.error("[{}]: Error fetched device {} info", entity.getEntityID(), entity);
            entity.setStatusError(ex);
        }
    }

    private void processEndpointStatus(Integer dp, Object rawValue) {
        TuyaDeviceEndpoint endpoint = endpoints.values().stream().filter(p -> p.getDp() == dp).findAny().orElse(null);
        if (endpoint != null) {
            Pair<Long, Object> pair = statusCache.get(dp);
            if (pair == null || Duration.ofMillis(System.currentTimeMillis() - pair.getKey()).getSeconds() > 10) {
                // skip update if the endpoint is off!
                return;
            }

            if (!endpoint.processEndpointStatus(rawValue)) {
                log.warn("[{}]: Could not update endpoint '{}' with value '{}'. Datatype incompatible.",
                        entity.getEntityID(), endpoint.getDeviceEntityID(), rawValue);
            }
        } else {
            List<TuyaDeviceEndpoint> dp2Endpoints = endpoints.values().stream().filter(p -> p.getDp2() == dp).toList();
            if (dp2Endpoints.isEmpty()) {
                log.debug("[{}]: Could not find endpoint for dp '{}' in thing '{}'", entity.getEntityID(), dp, entity.getTitle());
            } else {
                if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                    for (TuyaDeviceEndpoint dp2Endpoint : dp2Endpoints) {
                        dp2Endpoint.processEndpointStatus(rawValue);
                    }
                    return;
                }
                log.warn("[{}]: Could not update endpoint '{}' with value {}. Datatype incompatible.",
                        entity.getEntityID(), dp2Endpoints, rawValue);
            }
        }
    }

    public static void destroyAll() {
        udpDiscoveryListener.deactivate();
        eventLoopGroup.shutdownGracefully();
    }

    public void send(@NotNull Map<Integer, @Nullable Object> commands) {
        TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
        if (tuyaDeviceCommunicator != null) {
            tuyaDeviceCommunicator.set(commands);
        }
    }

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        if (this.models == null) {
            Set<String> endpoints = schemaDps.values().stream().map(s -> s.code).collect(Collectors.toSet());
            this.models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), endpoints);
        }
        return this.models;
    }
}

package org.homio.addon.tuya.service;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.tuya.TuyaEntrypoint.eventLoopGroup;
import static org.homio.addon.tuya.TuyaEntrypoint.udpDiscoveryListener;
import static org.homio.addon.tuya.service.TuyaDiscoveryService.updateTuyaDeviceEntity;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.TuyaDeviceEndpoint;
import org.homio.addon.tuya.TuyaDeviceEndpoint.TuyaEndpointType;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.addon.tuya.internal.local.DeviceInfoSubscriber;
import org.homio.addon.tuya.internal.local.DeviceStatusListener;
import org.homio.addon.tuya.internal.local.TuyaDeviceCommunicator;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles commands and state updates
 */
@Log4j2
public class TuyaDeviceService extends ServiceInstance<TuyaDeviceEntity> implements DeviceInfoSubscriber, DeviceStatusListener {

    private static final SchemaDp LAST_SEEN_SCHEMA_DP = new SchemaDp()
        .setCode(ENDPOINT_LAST_SEEN)
        .setType(TuyaEndpointType.number);

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
        new ConfigDeviceDefinitionService("tuya-devices.json");

    private @NotNull Optional<TuyaDeviceCommunicator> tuyaDeviceCommunicator = Optional.empty();

    @Getter
    private final @NotNull Map<String, TuyaDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private @Nullable ThreadContext<Void> pollingJob;
    private @Nullable ThreadContext<Void> reconnectFuture;
    private @NotNull Map<String, SchemaDp> schemaDps = Map.of();
    private List<ConfigDeviceDefinition> models;

    public TuyaDeviceService(EntityContext entityContext) {
        super(entityContext);
    }

    @Override
    public void processDeviceStatus(String cid, @NotNull Map<Integer, Object> deviceStatus) {
        log.debug("[{}]: received status message '{}'", entity.getEntityID(), deviceStatus);

        if (deviceStatus.isEmpty()) {
            // if status is empty -> need to use control method to request device status
            Map<Integer, @Nullable Object> commandRequest = new HashMap<>();
            endpoints.values().forEach(p -> commandRequest.put(p.getDp(), null));
            endpoints.values().stream().filter(p -> p.getDp2() != null).forEach(p -> commandRequest.put(p.getDp2(), null));

            tuyaDeviceCommunicator.ifPresent(c -> c.sendCommand(commandRequest));
        } else {
            deviceStatus.forEach(this::processEndpointStatus);
        }
    }

    @Override
    public void destroy() throws Exception {
        closeAll();
        udpDiscoveryListener.unregisterListener(entity.getIeeeAddress());
    }

    private void closeAll() {
        tuyaDeviceCommunicator.ifPresent(TuyaDeviceCommunicator::dispose);
        tuyaDeviceCommunicator = Optional.empty();
        if (EntityContextBGP.cancel(reconnectFuture)) {
            reconnectFuture = null;
        }
        if (EntityContextBGP.cancel(pollingJob)) {
            pollingJob = null;
        }
    }

    @Override
    @SneakyThrows
    public void initialize() {
        this.closeAll(); // stop all before initialize
        createOrUpdateDeviceGroup();
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
                        if (CONFIG_DEVICE_SERVICE.isIgnoreEndpoint(entry.getKey())) {
                            log.info("[{}]: ({}): Skip endpoint: {}", entityID, entity.getTitle(), entry.getKey());
                        } else {
                            ConfigDeviceEndpoint endpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(entry.getKey());
                            endpoints.put(entry.getKey(), new TuyaDeviceEndpoint(entry.getValue(), entityContext, entity, endpoint));
                        }
                    }
                    ConfigDeviceEndpoint endpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(ENDPOINT_LAST_SEEN);
                    TuyaDeviceEndpoint lastSeenEndpoint = new TuyaDeviceEndpoint(LAST_SEEN_SCHEMA_DP, entityContext, entity, endpoint) {
                        @Override
                        public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
                            uiInputBuilder.addDuration(getValue().longValue(), null);
                        }
                    };
                    lastSeenEndpoint.writeValue(System.currentTimeMillis());
                    endpoints.put(ENDPOINT_LAST_SEEN, lastSeenEndpoint);
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
        } catch (Exception ex) {
            entity.setStatusError(ex);
            log.error("[{}]: Error during initialize tuya device: {}", entity.getEntityID(), CommonUtils.getErrorMessage(ex));
        }
    }

    private void createOrUpdateDeviceGroup() {
        Icon icon = new Icon(
            CONFIG_DEVICE_SERVICE.getDeviceIcon(findDevices(), "fas fa-server"),
            CONFIG_DEVICE_SERVICE.getDeviceIconColor(findDevices(), UI.Color.random())
        );
        entityContext.var().createGroup("tuya", "Tuya", true, new Icon("fas fa-fish-fins", "#D68C38"));
        entityContext.var().createGroup("tuya", requireNonNull(entity.getIeeeAddress()), getDeviceFullName(), true,
            icon, getGroupDescription());
    }

    public String getGroupDescription() {
        return "${%s} [%s]".formatted(entity.getTitle(), entity.getIeeeAddress());
    }

    private String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
                entity.getTitle(),
                entity.getIeeeAddress(),
                defaultIfEmpty(entity.getPlace(), "place_not_set"));
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
    public void onDisconnected(@NotNull String message) {
        entity.setStatus(Status.ERROR, message);
        if (EntityContextBGP.cancel(pollingJob)) {
            pollingJob = null;
        }
        tuyaDeviceCommunicator.ifPresent(communicator -> {
            ThreadContext<Void> reconnectFuture = this.reconnectFuture;
            // only re-connect if a device is present, we are not disposing the thing and either the reconnectFuture is
            // empty or already done
            if (reconnectFuture == null || reconnectFuture.isStopped()) {
                this.reconnectFuture = entityContext.bgp().builder("tuya-device-connect-%s".formatted(entity.getEntityID()))
                                                    .delay(Duration.ofSeconds(5))
                                                    .execute(communicator::connect);
            }
        });
    }

    @Override
    public void onConnected() {
        entity.setStatus(Status.ONLINE);
        int pollingInterval = entity.getPollingInterval();
        tuyaDeviceCommunicator.ifPresent(communicator -> {
            if (pollingInterval > 0) {
                pollingJob = entityContext.bgp().builder("tuya-device-pull-%s".formatted(entity.getEntityID()))
                                          .intervalWithDelay(Duration.ofSeconds(pollingInterval))
                                          .execute(communicator::refreshStatus);
            }
        });
    }

    @Override
    public void deviceInfoChanged(DeviceInfo deviceInfo) {
        log.info("[{}]: Configuring IP address '{}' for thing '{}'.", entity.getEntityID(), deviceInfo, entity.getTitle());

        tuyaDeviceCommunicator.ifPresent(TuyaDeviceCommunicator::dispose);
        if (!deviceInfo.ip().equals(entity.getIp())) {
            entityContext.save(entity.setIp(deviceInfo.ip()));
        }
        entity.setStatus(Status.WAITING, null);

        tuyaDeviceCommunicator = Optional.of(new TuyaDeviceCommunicator(this, eventLoopGroup,
            deviceInfo.ip(), deviceInfo.protocolVersion(), entity));
    }

    @SneakyThrows
    public void tryFetchDeviceInfo() {
        log.info("[{}]: Fetching device {} info", entity.getEntityID(), entity);
        TuyaOpenAPI api = entityContext.getBean(TuyaOpenAPI.class);
        entity.setStatus(Status.INITIALIZE);
        try {
            TuyaDeviceDTO tuyaDevice = api.getDevice(entity.getIeeeAddress(), entity);
            log.info("[{}]: Fetched device {} info successfully", entity.getEntityID(), entity);
            if (updateTuyaDeviceEntity(tuyaDevice, api, entity)) {
                entityContext.save(entity);
            }

        } catch (Exception ex) {
            log.error("[{}]: Error fetched device {} info", entity.getEntityID(), entity);
            entity.setStatusError(ex);
        }
    }

    private void processEndpointStatus(Integer dp, Object rawValue) {
        TuyaDeviceEndpoint endpoint = endpoints.values().stream().filter(p -> p.getDp() == dp).findAny().orElse(null);
        if (endpoint != null) {
            if (!endpoint.writeValue(rawValue)) {
                log.warn("[{}]: Could not update endpoint '{}' with value '{}'. Datatype incompatible.",
                    entity.getEntityID(), endpoint.getDeviceID(), rawValue);
            }
        } else {
            List<TuyaDeviceEndpoint> dp2Endpoints = endpoints.values().stream().filter(p -> Objects.equals(p.getDp2(), dp)).toList();
            if (dp2Endpoints.isEmpty()) {
                log.debug("[{}]: Could not find endpoint for dp '{}' in thing '{}'", entity.getEntityID(), dp, entity.getTitle());
            } else {
                if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                    for (TuyaDeviceEndpoint dp2Endpoint : dp2Endpoints) {
                        dp2Endpoint.writeValue(rawValue);
                    }
                    return;
                }
                log.warn("[{}]: Could not update endpoint '{}' with value {}. Datatype incompatible.",
                    entity.getEntityID(), dp2Endpoints, rawValue);
            }
        }
        endpoints.get(ENDPOINT_LAST_SEEN).writeValue(System.currentTimeMillis());
    }

    public ActionResponseModel send(@NotNull Map<Integer, @Nullable Object> commands) {
        return tuyaDeviceCommunicator.map(communicator -> communicator.sendCommand(commands)).orElse(null);
    }

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        if (this.models == null) {
            Set<String> endpoints = schemaDps.values().stream().map(SchemaDp::getCode).collect(Collectors.toSet());
            this.models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), endpoints);
        }
        return this.models;
    }
}

package org.homio.addon.imou.service;

import static java.util.Objects.requireNonNull;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.imou.ImouDeviceEndpoint;
import org.homio.addon.imou.ImouDeviceEntity;
import org.homio.addon.imou.ImouProjectEntity;
import org.homio.addon.imou.internal.cloud.ImouAPI;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceCallbackUrlDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceNightVisionModeDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceOnlineStatusDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDevicePowerInfoDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouSDCardDTO.ImouSDCardStatusDTO;
import org.homio.api.EntityContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.UI;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles commands and state updates
 */
@Log4j2
public class ImouDeviceService extends ServiceInstance<ImouDeviceEntity> {

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
        new ConfigDeviceDefinitionService("imou-devices.json");

    @Getter
    private final @NotNull Map<String, ImouDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private final String deviceId;
    private final ImouAPI api;
    //private @Nullable ThreadContext<Void> pollingJob;
    //private @Nullable ThreadContext<Void> reconnectFuture;
    private List<ConfigDeviceDefinition> models;

    public ImouDeviceService(EntityContext entityContext, ImouDeviceEntity entity) {
        super(entityContext, entity, true);
        this.deviceId = entity.getIeeeAddress();
        this.api = entityContext.getBean(ImouAPI.class);
    }

    /*@Override
    public void processDeviceStatus(String cid, @NotNull Map<Integer, Object> deviceStatus) {
        log.debug("[{}]: received status message '{}'", entity.getEntityID(), deviceStatus);

        if (deviceStatus.isEmpty()) {
            // if status is empty -> need to use control method to request device status
            Map<Integer, @Nullable Object> commandRequest = new HashMap<>();
            endpoints.values().forEach(p -> {
                if (p.getDp() != 0) {
                    commandRequest.put(p.getDp(), null);
                }
            });
            endpoints.values().stream().filter(p -> p.getDp2() != null).forEach(p -> commandRequest.put(p.getDp2(), null));

            imouDeviceCommunicator.ifPresent(c -> c.sendCommand(commandRequest));
        } else {
            deviceStatus.forEach(this::externalUpdate);
        }
    }*/

    @Override
    public void destroy() {
        //closeAll();
    }

    /*private void closeAll() {
        imouDeviceCommunicator.ifPresent(ImouDeviceCommunicator::dispose);
        imouDeviceCommunicator = Optional.empty();
        if (EntityContextBGP.cancel(reconnectFuture)) {
            reconnectFuture = null;
        }
        if (EntityContextBGP.cancel(pollingJob)) {
            pollingJob = null;
        }
    }*/

    @Override
    @SneakyThrows
    public void initialize() {
        // this.closeAll(); // stop all before initialize
        createOrUpdateDeviceGroup();
        try {
            if (StringUtils.isEmpty(entity.getIeeeAddress())) {
                setEntityStatus(ERROR, "Empty device id");
                return;
            }
            // check if we have endpoints and add them if available
            if (endpoints.isEmpty()) {
                List<String> capabilities = entity.getCapabilities();
                if (!capabilities.isEmpty()) {
                    // fallback to retrieved schema
                    capabilities = capabilities.stream().filter(c -> !CONFIG_DEVICE_SERVICE.isIgnoreEndpoint(c)).sorted().collect(Collectors.toList());
                    buildEndpoints(capabilities);
                } else {
                    setEntityStatus(OFFLINE, "No endpoints found");
                }
            }
        } catch (Exception ex) {
            setEntityStatus(ERROR, CommonUtils.getErrorMessage(ex));
            log.error("[{}]: Error during initialize imou device: {}", entity.getEntityID(), CommonUtils.getErrorMessage(ex));
        }
    }

    private void buildEndpoints(List<String> capabilities) {
        Set<String> fixedCapabilities = capabilities.stream().map(c -> c.toLowerCase().replaceAll("v\\d$", "")).collect(Collectors.toSet());
        Map<String, ConfigDeviceEndpoint> switches = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().values().stream()
                                                                          .filter(de -> de.getMetadata().optString("type").equals("switch"))
                                                                          .filter(de -> fixedCapabilities.contains(de.getName().toLowerCase()))
                                                                          .collect(Collectors.toMap(ConfigDeviceEndpoint::getName, e -> e));
        for (Entry<String, ConfigDeviceEndpoint> switchEntry : switches.entrySet()) {
            ImouDeviceEndpoint deviceEndpoint = addEndpoint(new ImouDeviceEndpoint(switchEntry.getKey(), true,
                true, EndpointType.bool, new Icon(switchEntry.getValue().getIcon(), switchEntry.getValue().getIconColor()), entity));
            deviceEndpoint.setInitialValue(OnOffType.OFF);
        }

        addBatteryEndpoint(capabilities);
        addStorageUsedEndpoint(capabilities);
        addCallbackUrlEndpoint();

        // add motionAlarm binary sensor
        if (capabilities.remove("AlarmMD")) {
            addEndpoint(new ImouDeviceEndpoint("motionAlarm", true,
                false, EndpointType.bool, null, entity));
        }

        ImouDeviceOnlineStatusDTO.Status status = api.getDeviceStatus(deviceId).getOnLine();

        // add status sensor
        addStatusEndpoint(status);
        // add nightVisionMode select
        addNightVisionModeEndpoint(capabilities);
        // add restart button
        addRestartButton();
        // add refresh button
        addRefreshButton();

        // update the status of all the sensors (if the device is online)
        if (status == ImouDeviceOnlineStatusDTO.Status.Online) {
            log.info("Fetch device {} endpoint statuses", entity);
            for (ImouDeviceEndpoint endpoint : endpoints.values()) {
                Runnable initializer = endpoint.getInitializer();
                if (initializer != null) {
                    initializer.run();
                } else if (endpoint.getReader() != null) {
                    endpoint.setInitialValue(endpoint.getReader().get());
                }
                // fire create variable after first init
                endpoint.getOrCreateVariable();
            }
        }

        /**
         * # add callbackUrl sensor
         * # add online binary sensor
         * # add restartDevice button
         * # add refreshData button
         * # add siren siren
         * # add cameras HD/SD
         */

        capabilities.removeAll(switches.keySet());
        int order = 1000;
        for (String capability : capabilities) {
            ImouDeviceEndpoint deviceEndpoint = addEndpoint(new ImouDeviceEndpoint(capability, false,
                false, EndpointType.bool, new Icon("fa fa-fw fa-flask-vial"), entity));
            deviceEndpoint.setOrder(order++);
            deviceEndpoint.setInitialValue(OnOffType.ON);
        }
    }

    private void addBatteryEndpoint(List<String> capabilities) {
        if (capabilities.remove("Dormant")) {
            ImouDeviceEndpoint endpoint = addEndpoint(new ImouDeviceEndpoint("battery", true,
                false, EndpointType.number, null, entity));
            endpoint.setReader(() -> {
                ImouDevicePowerInfoDTO dto = api.request("getDevicePowerInfo", deviceId, ImouDevicePowerInfoDTO.class);
                return new DecimalType(dto.getElectricitys().getElectric());
            });
        }
    }

    private void addCallbackUrlEndpoint() {
        ImouDeviceEndpoint endpoint = addEndpoint(new ImouDeviceEndpoint("callbackUrl", true,
            false, EndpointType.string, null, entity));
        endpoint.setReader(() -> {
            ImouDeviceCallbackUrlDTO dto = api.request("getMessageCallback", deviceId, ImouDeviceCallbackUrlDTO.class);
            return new StringType(dto.getCallbackUrl());
        });
    }

    private void addStorageUsedEndpoint(List<String> capabilities) {
        if (capabilities.remove("LocalStorage")) {
            ImouDeviceEndpoint endpoint = addEndpoint(new ImouDeviceEndpoint("storageUsed", true,
                false, EndpointType.string, null, entity));
            endpoint.setReader(() -> {
                ImouSDCardStatusDTO status = api.getDeviceSDCardStatus(this.deviceId);
                if (status != null) {
                    return new StringType(status.toString());
                }
                return new StringType("-");
            });
        }
    }

    private void addStatusEndpoint(ImouDeviceOnlineStatusDTO.Status status) {
        ImouDeviceEndpoint statusEndpoint = addEndpoint(new ImouDeviceEndpoint("status", true,
            false, EndpointType.string, null, entity));
        statusEndpoint.setReader(() -> new StringType(api.getDeviceStatus(deviceId).getOnLine().name()));
        statusEndpoint.setInitialValue(new StringType(status.name()));
        statusEndpoint.setInitializer(() -> {});
    }

    private void addRefreshButton() {
        addTriggerEndpoint("refreshData", "RESTART", new Icon("fas fa-power-off", Color.BLUE), state -> {
            for (ImouDeviceEndpoint endpoint : endpoints.values()) {
                endpoint.readValue();
            }
        });
    }

    private void addRestartButton() {
        addTriggerEndpoint("restartDevice", "RESTART", new Icon("fas fa-power-off", Color.RED), state -> {
            api.restart(deviceId);
        });
    }

    private void addTriggerEndpoint(String endpointId, String confirmBtn, Icon buttonIcon, Consumer<State> updateHandler) {
        addEndpoint(new ImouDeviceEndpoint(endpointId, true,
            true, EndpointType.trigger, buttonIcon, entity) {
            @Override
            public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
                uiInputBuilder.addButton(getEntityID(), buttonIcon, (entityContext, params) -> {
                                  updateHandler.accept(null);
                                  return null;
                              })
                              .setConfirmMessage("W.CONFIRM." + confirmBtn)
                              .setConfirmMessageDialogColor(buttonIcon.getColor())
                              .setDisabled(!getEndpoints().get("status").getValue().stringValue()
                                                          .equals(ImouDeviceOnlineStatusDTO.Status.Online.name()));
                return uiInputBuilder;
            }
        });
    }

    private ImouDeviceEndpoint addEndpoint(ImouDeviceEndpoint endpoint) {
        endpoints.put(endpoint.getEndpointEntityID(), endpoint);
        return endpoint;
    }

    private void addNightVisionModeEndpoint(List<String> capabilities) {
        if (capabilities.remove("NVM")) {
            ImouDeviceEndpoint deviceEndpoint = addEndpoint(new ImouDeviceEndpoint("nightVisionMode", true,
                false, EndpointType.select, null, entity));
            deviceEndpoint.setReader(() -> new StringType(api.getNightVisionMode(deviceId).getMode()));
            deviceEndpoint.setInitializer(() -> {
                ImouDeviceNightVisionModeDTO dto = api.getNightVisionMode(deviceId);
                deviceEndpoint.setRange(OptionModel.list(dto.getModes()));
                deviceEndpoint.setInitialValue(new StringType(dto.getMode()));
            });
        }
    }

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        if (models == null && !entity.getCapabilities().isEmpty()) {
            models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), new HashSet<>(entity.getCapabilities()));
        }
        return models == null ? List.of() : models;
    }

    private void setEntityStatus(@NotNull Status status, @Nullable String message) {
        if (entity.getStatus() != status || !Objects.equals(entity.getStatusMessage(), message)) {
            entity.setStatus(status, message);
            getEndpoints().get(ENDPOINT_DEVICE_STATUS).setValue(new StringType(toString()), true);
            ImouProjectEntity projectEntity = ImouAPI.getProjectEntity();
            if (projectEntity != null) {
                projectEntity.getService().updateNotificationBlock();
            }
        }
    }

    public String getGroupDescription() {
        if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
            return entity.getIeeeAddress();
        }
        return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
    }

    /*@Override
    public void onDisconnected(@NotNull String message) {
        setEntityStatus(ERROR, message);
        if (EntityContextBGP.cancel(pollingJob)) {
            pollingJob = null;
        }
        scheduleReconnect();
    }*/

   /* @Override
    public void onConnected() {
        if (!entity.getStatus().isOnline()) {
            setEntityStatus(ONLINE, null);
            scheduleRefreshDeviceStatus();
        }
    }*/

   /* @Override
    public void deviceInfoChanged(DeviceInfo deviceInfo) {
        log.info("[{}]: Configuring IP address '{}' for thing '{}'.", entity.getEntityID(), deviceInfo, entity.getTitle());

        imouDeviceCommunicator.ifPresent(ImouDeviceCommunicator::dispose);
        if (!deviceInfo.ip().equals(entity.getIp())) {
            entityContext.save(entity.setIp(deviceInfo.ip()));
        }
        setEntityStatus(WAITING, null);

        imouDeviceCommunicator = Optional.of(new ImouDeviceCommunicator(this, eventLoopGroup,
            deviceInfo.ip(), deviceInfo.protocolVersion(), entity));
    }*/

    /*private void externalUpdate(Integer dp, Object rawValue) {
        endpoints.get(ENDPOINT_LAST_SEEN).setValue(new DecimalType(System.currentTimeMillis()), true);
        ImouDeviceEndpoint endpoint = endpoints.values().stream().filter(p -> p.getDp() == dp).findAny().orElse(null);
        if (endpoint != null) {
            State state = endpoint.rawValueToState(rawValue);
            if (state == null) {
                log.warn("[{}]: Could not update endpoint '{}' with value '{}'. Datatype incompatible.",
                    entity.getEntityID(), endpoint.getDeviceID(), rawValue);
            } else {
                endpoint.setValue(state, true);
            }
        } else {
            List<ImouDeviceEndpoint> dp2Endpoints = endpoints.values().stream().filter(p -> Objects.equals(p.getDp2(), dp)).toList();
            if (dp2Endpoints.isEmpty()) {
                log.debug("[{}]: Could not find endpoint for dp '{}' in thing '{}'", entity.getEntityID(), dp, entity.getTitle());
            } else {
                if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                    for (ImouDeviceEndpoint dp2Endpoint : dp2Endpoints) {
                        dp2Endpoint.setValue(State.of(rawValue), true);
                    }
                    return;
                }
                log.warn("[{}]: Could not update endpoint '{}' with value {}. Datatype incompatible.",
                    entity.getEntityID(), dp2Endpoints, rawValue);
            }
        }
    }*/

    public ActionResponseModel send(@NotNull Map<Integer, @Nullable Object> commands) {
        return null;
        // return imouDeviceCommunicator.map(communicator -> communicator.sendCommand(commands)).orElse(null);
    }

    private void createOrUpdateDeviceGroup() {
        List<ConfigDeviceDefinition> devices = findDevices();
        Icon icon = new Icon(
            CONFIG_DEVICE_SERVICE.getDeviceIcon(devices, "fas fa-server"),
            CONFIG_DEVICE_SERVICE.getDeviceIconColor(devices, UI.Color.random())
        );
        entityContext.var().createGroup("imou", "Imou", builder ->
            builder.setIcon(new Icon("fas fa-u", IMOU_COLOR)).setLocked(true));
        entityContext.var().createSubGroup("imou", requireNonNull(entity.getIeeeAddress()), entity.getDeviceFullName(), builder -> {
            builder.setIcon(icon).setDescription(getGroupDescription()).setLocked(true);
        });
    }

    /*private void scheduleRefreshDeviceStatus() {
        if (entity.getStatus().isOnline()) {
            // request all statuses
            //   imouDeviceCommunicator.ifPresent(ImouDeviceCommunicator::requestStatus);

            imouDeviceCommunicator.ifPresent(communicator -> {
                entityContext.bgp().builder("imou-pull-all-%s".formatted(entity.getIeeeAddress()))
                             .delay(Duration.ofSeconds(5))
                             .execute(communicator::requestStatus);
                pollingJob = entityContext.bgp().builder("imou-pull-%s".formatted(entity.getIeeeAddress()))
                                          .intervalWithDelay(Duration.ofSeconds(entity.getPollingInterval()))
                                          .execute(communicator::refreshStatus);
            });
        }
    }*/

    /*private void scheduleReconnect() {
        imouDeviceCommunicator.ifPresent(communicator -> {
            ThreadContext<Void> reconnectFuture = this.reconnectFuture;
            // only re-connect if a device is present, we are not disposing the thing and either the reconnectFuture is
            // empty or already done
            if (reconnectFuture == null || reconnectFuture.isStopped()) {
                this.reconnectFuture =
                    entityContext.bgp().builder("imou-connect-%s".formatted(entity.getIeeeAddress()))
                                 .delay(Duration.ofSeconds(entity.getReconnectInterval()))
                                 .execute(() -> {
                                     if (!entity.getStatus().isOnline()) {
                                         setEntityStatus(INITIALIZE, null);
                                         communicator.connect();
                                     }
                                 });
            }
        });
    }*/
}

package org.homio.addon.imou.service;

import static java.util.Objects.requireNonNull;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.SLEEPING;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.Status.UPDATING;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;

import java.time.Duration;
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
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceAlarmMessageDTO.Alarm;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceCallbackUrlDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceNightVisionModeDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDevicePowerInfoDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceStatusDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouSDCardDTO.ImouSDCardStatusDTO;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
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
import org.homio.api.ui.UI.Image.Snapshot;
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
    private final @NotNull @Getter ImouAPI api;
    private List<ConfigDeviceDefinition> models;
    private final Snapshot snapshot;
    private boolean initialized;
    private ThreadContext<Void> deviceStatusCheck;
    private int order = 100;
    private ImouDeviceEndpoint statusEndpoint;

    public ImouDeviceService(Context context, ImouDeviceEntity entity) {
        super(context, entity, true);
        this.deviceId = entity.getIeeeAddress();
        this.api = context.getBean(ImouAPI.class);
        this.snapshot = new Snapshot(entity.getFetchDataInterval());
    }

    @Override
    public void destroy() {
        if (deviceStatusCheck != null) {
            deviceStatusCheck.cancel();
        }
    }

    @Override
    @SneakyThrows
    public void initialize() {
        createOrUpdateDeviceGroup();
        try {
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

            this.deviceStatusCheck =
                context.bgp().builder("imou-fetch-data-" + entityID)
                       .cancelOnError(false)
                       .intervalWithDelay(Duration.ofSeconds(entity.getFetchDataInterval()))
                       .execute(() -> {
                           if (!entity.getStatus().isOnline()) {
                               statusEndpoint.readValue();
                           } else {
                               for (ImouDeviceEndpoint endpoint : endpoints.values()) {
                                   if (endpoint.getReader() != null) {
                                       endpoint.readValue();
                                   }
                               }
                           }
                       });
        } catch (Exception ex) {
            setEntityStatus(ERROR, CommonUtils.getErrorMessage(ex));
            log.error("[{}]: Error during initialize imou device: {}", entity.getEntityID(), CommonUtils.getErrorMessage(ex));
        }
    }

    public String getCallbackUrl() {
        return api.getMessageCallback(deviceId).getCallbackUrl();
    }

    public void updateCallbackUrl(String callbackUrl) {
        api.setMessageCallback(callbackUrl);
    }

    public byte[] getSnapshot() {
        return snapshot.getSnapshot(() -> {
            new Thread(this::takeSnapshotSync).start();
            return null;
        });
    }

    public void takeSnapshot() {
        snapshot.setSnapshot(api.getSnapshot(deviceId));
    }

    private void takeSnapshotSync() {
        if (snapshot.setSnapshot(api.getSnapshot(deviceId))) {
            context.ui().updateItem(getEntity(), "snapshot", snapshot.getLatestSnapshot());
        }
    }

    private void buildEndpoints(List<String> capabilities) {
        Map<String, ConfigDeviceEndpoint> switches = getSwitches(capabilities);
        for (Entry<String, ConfigDeviceEndpoint> switchEntry : switches.entrySet()) {
            ImouDeviceEndpoint endpoint = addEndpoint(switchEntry.getKey(), EndpointType.bool);
            endpoint.setInitialValue(OnOffType.OFF);
            endpoint.setReader(() -> {
                ImouDeviceStatusDTO dto = api.request("getDeviceCameraStatus", deviceId, "enableType", endpoint.getEndpointEntityID(),
                    ImouDeviceStatusDTO.class);
                return OnOffType.of("on".equals(dto.getStatus()));
            });
            endpoint.setUpdateHandler(state ->
                api.setDeviceCameraStatus(deviceId, endpoint.getEndpointEntityID(), state.boolValue()));
        }

        Status status = api.getDeviceStatus(deviceId).getStatus();
        entity.setStatus(status);

        addStatusEndpoint(status);
        addBatteryEndpoint(capabilities);
        addRestartButton();
        addRefreshButton();
        addNightVisionModeEndpoint(capabilities);
        addMotionAlarm(capabilities);
        addStorageUsedEndpoint(capabilities);
        addCallbackUrlEndpoint();

        tryInitializeEndpoints(status);

        /*
         * # add online binary sensor
         * # add siren siren
         * # add cameras HD/SD
         */

        capabilities.removeAll(switches.keySet());
        for (String capability : capabilities) {
            ImouDeviceEndpoint deviceEndpoint = addEndpoint(capability, EndpointType.bool);
            deviceEndpoint.setIcon(new Icon("fa fa-fw fa-flask-vial"));
            deviceEndpoint.setInitialValue(OnOffType.ON);
        }
    }

    @NotNull
    private static Map<String, ConfigDeviceEndpoint> getSwitches(List<String> capabilities) {
        Set<String> fixedCapabilities = capabilities.stream()
                                                    .map(c -> c.toLowerCase().replaceAll("v\\d$", ""))
                                                    .collect(Collectors.toSet());
        return CONFIG_DEVICE_SERVICE.getDeviceEndpoints().values().stream()
                                    .filter(de -> de.getMetadata().optString("type").equals("switch"))
                                    .filter(de -> fixedCapabilities.contains(de.getName().toLowerCase()))
                                    .collect(Collectors.toMap(ConfigDeviceEndpoint::getName, e -> e));
    }

    private void addMotionAlarm(List<String> capabilities) {
        if (capabilities.remove("AlarmMD")) {
            ImouDeviceEndpoint endpoint = addEndpoint("motionAlarm", EndpointType.bool);
            endpoint.setReader(() -> {
                List<Alarm> alarms = api.getAlarmMessages(deviceId).getAlarms();
                return new StringType(alarms.isEmpty() ? "-" : alarms.get(0).getLocalDate());
            });
        }
    }

    private void addBatteryEndpoint(List<String> capabilities) {
        if (capabilities.remove("Dormant")) {
            ImouDeviceEndpoint endpoint = addEndpoint("battery", EndpointType.number);
            endpoint.setReader(() -> {
                ImouDevicePowerInfoDTO dto = api.request("getDevicePowerInfo", deviceId, ImouDevicePowerInfoDTO.class);
                return new DecimalType(dto.getElectricitys().getElectric());
            });
        }
    }

    private void addCallbackUrlEndpoint() {
        ImouDeviceEndpoint endpoint = addEndpoint("callbackUrl", EndpointType.string);
        endpoint.setReader(() -> {
            ImouDeviceCallbackUrlDTO dto = api.getMessageCallback(deviceId);
            if (dto.getStatus().equals("off")) {
                return new StringType("OFF");
            }
            return new StringType(dto.getCallbackUrl());
        });
    }

    private void addStorageUsedEndpoint(List<String> capabilities) {
        if (capabilities.remove("LocalStorage")) {
            ImouDeviceEndpoint endpoint = addEndpoint("storageUsed", EndpointType.string);
            endpoint.setReader(() -> {
                try {
                    ImouSDCardStatusDTO status = api.getDeviceSDCardStatus(this.deviceId);
                    return new StringType(status.toString());
                } catch (Exception ex) {
                    return new StringType(ex.getMessage());
                }
            });
        }
    }

    private void addStatusEndpoint(Status status) {
        statusEndpoint = addEndpoint(new ImouDeviceEndpoint(ENDPOINT_DEVICE_STATUS, EndpointType.select, entity));
        statusEndpoint.setRange(OptionModel.list(Status.set(ONLINE, OFFLINE, UNKNOWN, UPDATING, SLEEPING)));
        statusEndpoint.setInitialValue(new StringType(status.name()));
        statusEndpoint.setReader(() -> {
            Status newStatus = api.getDeviceStatus(deviceId).getStatus();
            tryInitializeEndpoints(newStatus);
            entity.setStatus(newStatus);
            return new StringType(newStatus.name());
        });
        statusEndpoint.setInitializer(() -> {}); // to avoid call reader first time
    }

    private void tryInitializeEndpoints(@NotNull Status newStatus) {
        if (!initialized && newStatus.isOnline()) {
            initialized = true;
            log.info("[{}]: Fetch device {} endpoint statuses", entityID, entity);
            for (ImouDeviceEndpoint endpoint : endpoints.values()) {
                Runnable initializer = endpoint.getInitializer();
                if (initializer != null) {
                    initializer.run();
                } else if (endpoint.getReader() != null) {
                    endpoint.setInitialValue(endpoint.getReader().get());
                }
                endpoint.getOrCreateVariable();
            }
        }
    }

    private void addRefreshButton() {
        addTriggerEndpoint("refreshData", "FETCH_DATA_FROM_SERVER", state -> {
            for (ImouDeviceEndpoint endpoint : endpoints.values()) {
                endpoint.readValue();
            }
        });
    }

    private void addRestartButton() {
        addTriggerEndpoint("restartDevice", "RESTART_DEVICE", state ->
            api.restart(deviceId));
    }

    private void addTriggerEndpoint(String endpointId, String confirmBtn, Consumer<State> updateHandler) {
        addEndpoint(new ImouDeviceEndpoint(endpointId, EndpointType.trigger, entity) {
            @Override
            public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
                uiInputBuilder.addButton(getEntityID(), getIcon(), (context, params) -> {
                                  setValue(OnOffType.ON, false);
                                  updateHandler.accept(null);
                                  return null;
                              })
                              .setText("")
                              .setConfirmMessage("W.CONFIRM." + confirmBtn)
                              .setConfirmMessageDialogColor(UI.Color.darker(getIcon().getColor(), 0.6f))
                              .setDisabled(!statusEndpoint.getValue().stringValue().equals(Status.ONLINE.name()));
                return uiInputBuilder;
            }
        });
    }

    private ImouDeviceEndpoint addEndpoint(String endpointId, EndpointType endpointType) {
        return addEndpoint(new ImouDeviceEndpoint(endpointId, endpointType, entity));
    }

    private ImouDeviceEndpoint addEndpoint(ImouDeviceEndpoint endpoint) {
        if (endpoint.getOrder() > 1000) {
            endpoint.setOrder(order++);
        }
        endpoints.put(endpoint.getEndpointEntityID(), endpoint);
        return endpoint;
    }

    private void addNightVisionModeEndpoint(List<String> capabilities) {
        if (capabilities.remove("NVM")) {
            ImouDeviceEndpoint deviceEndpoint = addEndpoint("nightVisionMode", EndpointType.select);
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

    private void createOrUpdateDeviceGroup() {
        List<ConfigDeviceDefinition> devices = findDevices();
        Icon icon = new Icon(
            CONFIG_DEVICE_SERVICE.getDeviceIcon(devices, "fas fa-server"),
            CONFIG_DEVICE_SERVICE.getDeviceIconColor(devices, UI.Color.random())
        );
        context.var().createGroup("imou", "Imou", builder ->
            builder.setIcon(new Icon("fas fa-u", IMOU_COLOR)).setLocked(true));
        context.var().createSubGroup("imou", requireNonNull(entity.getIeeeAddress()), entity.getDeviceFullName(), builder ->
            builder.setIcon(icon).setDescription(getGroupDescription()).setLocked(true));
    }
}

package org.homio.addon.tuya.service;

import static org.homio.addon.tuya.service.TuyaDiscoveryService.updateTuyaDeviceEntity;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaDeviceProperty;
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
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link TuyaDeviceService} handles commands and state updates
 */
@Log4j2
public class TuyaDeviceService extends ServiceInstance<TuyaDeviceEntity> implements DeviceInfoSubscriber, DeviceStatusListener {

    private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private static final UdpDiscoveryListener udpDiscoveryListener = new UdpDiscoveryListener(eventLoopGroup);

    private @Nullable TuyaDeviceCommunicator tuyaDeviceCommunicator;
    private boolean oldColorMode = false;

    private boolean disposing = false;

    private final Map<Integer, Pair<Long, Object>> statusCache = new ConcurrentHashMap<>();

    @Getter
    private @NotNull Map<String, TuyaDeviceProperty> properties = new ConcurrentHashMap<>();
    private @Nullable ThreadContext<Void> pollingJob;
    private @Nullable ThreadContext<Void> reconnectFuture;

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
            properties.values().forEach(p -> commandRequest.put(p.getDp(), null));
            properties.values().stream().filter(p -> p.getDp2() != -1).forEach(p -> commandRequest.put(p.getDp2(), null));

            TuyaDeviceCommunicator tuyaDeviceCommunicator = this.tuyaDeviceCommunicator;
            if (tuyaDeviceCommunicator != null) {
                tuyaDeviceCommunicator.set(commandRequest);
            }
        } else {
            for (Entry<Integer, Object> entry : deviceStatus.entrySet()) {
                statusCache.put(entry.getKey(), Pair.of(System.currentTimeMillis(), entry.getValue()));
            }
            deviceStatus.forEach(this::processPropertyStatus);
        }
    }

    @Override
    public void destroy() throws Exception {
        disposing = true;
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
    }

    @Override
    @SneakyThrows
    public void initialize() {
        this.destroy(); // stop all before initialize
        try {
            if (StringUtils.isEmpty(entity.getIeeeAddress())) {
                throw new IllegalArgumentException("Empty device id");
            }
            if (StringUtils.isNotEmpty(entity.getIeeeAddress()) && StringUtils.isEmpty(entity.getLocalKey())) {
                entity.setStatus(Status.INITIALIZE);
                entityContext.bgp().builder("tuya-init-" + entity.getEntityID())
                             .delay(Duration.ofSeconds(1))
                             .onError(e -> entity.setStatus(Status.ERROR, CommonUtils.getErrorMessage(e)))
                             .execute(this::tryFetchDeviceInfo);
                return;
            }
            // check if we have properties and add them if available
            if (properties.isEmpty()) {
                // stored schemas are usually more complete
                List<SchemaDp> schemaDps = entity.getSchemaDps();
                if (!schemaDps.isEmpty()) {
                    // fallback to retrieved schema
                    addProperties(schemaDps);
                } else {
                    entity.setStatus(Status.OFFLINE, "Device has no properties");
                    return;
                }
            }

            if (!entity.getIp().isBlank()) {
                deviceInfoChanged(new DeviceInfo(entity.getIp(), entity.getProtocolVersion().getVersionString()));
            } else {
                entity.setStatus(Status.WAITING, "Waiting for IP address");
                udpDiscoveryListener.registerListener(entity.getIeeeAddress(), this);
            }

            disposing = false;
        } catch (Exception ex) {
            entity.setStatusError(ex);
            log.error("[{}]: Error during initialize tuya device: {}", entity.getEntityID(), CommonUtils.getErrorMessage(ex));
        }
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
            if (tuyaDeviceCommunicator != null && !disposing && (reconnectFuture == null || reconnectFuture.isStopped())) {
                this.reconnectFuture = entityContext.bgp().builder("tuya-device-connect-%s".formatted(entity.getEntityID()))
                                                    .delay(Duration.ofSeconds(5))
                                                    .execute(tuyaDeviceCommunicator::connect);
            }
        }
    }

    /*@Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            log.warn("[{}]: Channel '{}' received a command but device is not ONLINE. Discarding command.", entity.getEntityID(), channelUID);
            return;
        }

        Map<Integer, @Nullable Object> commandRequest = new HashMap<>();

        ChannelTypeUID channelTypeUID = channelIdToChannelTypeUID.get(channelUID.getId());
        ChannelConfiguration configuration = channelIdToConfiguration.get(channelUID.getId());
        if (channelTypeUID == null || configuration == null) {
            log.warn("[{}]: Could not determine channel type or configuration for channel '{}'. Discarding command.",
                    entity.getEntityID(), channelUID);
            return;
        }

        if (CHANNEL_TYPE_UID_COLOR.equals(channelTypeUID)) {
            if (command instanceof HSBType) {
                commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode((HSBType) command, oldColorMode));
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "colour");
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, ((HSBType) command).getBrightness().doubleValue() > 0.0);
                }
            } else if (command instanceof PercentType) {
                State oldState = channelStateCache.get(channelUID.getId());
                if (!(oldState instanceof HSBType)) {
                    log.debug("[{}]: Discarding command '{}' to channel '{}', cannot determine old state", entity.getEntityID(),
                        command, channelUID);
                    return;
                }
                HSBType newState = new HSBType(((HSBType) oldState).getHue(), ((HSBType) oldState).getSaturation(),
                        (PercentType) command);
                commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode(newState, oldColorMode));
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "colour");
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, ((PercentType) command).doubleValue() > 0.0);
                }
            } else if (command instanceof OnOffType) {
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, OnOffType.ON.equals(command));
                }
            }
        } else if (CHANNEL_TYPE_UID_DIMMER.equals(channelTypeUID)) {
            if (command instanceof PercentType) {
                int value = ConversionUtil.brightnessEncode((PercentType) command, 0, configuration.max);
                if (value >= configuration.min) {
                    commandRequest.put(configuration.dp, value);
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, value >= configuration.min);
                }
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "white");
                }
            } else if (command instanceof OnOffType) {
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, OnOffType.ON.equals(command));
                }
            }
        } else if (CHANNEL_TYPE_UID_STRING.equals(channelTypeUID)) {
            if (command instanceof StringType) {
                commandRequest.put(configuration.dp, command.toString());
            }
        } else if (CHANNEL_TYPE_UID_NUMBER.equals(channelTypeUID)) {
            if (command instanceof DecimalType) {
                commandRequest.put(configuration.dp, ((DecimalType) command).intValue());
            }
        } else if (CHANNEL_TYPE_UID_SWITCH.equals(channelTypeUID)) {
            if (command instanceof OnOffType) {
                commandRequest.put(configuration.dp, OnOffType.ON.equals(command));
            }
        }

        TuyaDevice tuyaDevice = this.tuyaDevice;
        if (!commandRequest.isEmpty() && tuyaDevice != null) {
            tuyaDevice.set(commandRequest);
        }
    }*/

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
        TuyaOpenAPI api = entityContext.getBean(TuyaOpenAPI.class);
        entity.setStatus(Status.INITIALIZE);
        try {
            TuyaDeviceDTO tuyaDevice = api.getDevice(entity.getIeeeAddress(), entity);
            updateTuyaDeviceEntity(tuyaDevice, api, entity);
            entityContext.save(entity);

        } catch (Exception ex) {
            entity.setStatusError(ex);

        }
    }

    private void processPropertyStatus(Integer dp, Object rawValue) {
        TuyaDeviceProperty property = properties.values().stream().filter(p -> p.getDp() == dp).findAny().orElse(null);
        if (property != null) {
            Pair<Long, Object> pair = statusCache.get(dp);
            if (pair == null || Duration.ofMillis(System.currentTimeMillis() - pair.getKey()).getSeconds() > 10) {
                // skip update if the property is off!
                return;
            }

            if (!property.getStateHandler().apply(rawValue)) {
                log.warn("[{}]: Could not update property '{}' with value '{}'. Datatype incompatible.",
                    entity.getEntityID(), property.getIeeeAddress(), rawValue);
            }
        } else {
            // try additional propertyDps, only OnOffType
            List<TuyaDeviceProperty> propertyIds = properties.values().stream().filter(p -> p.getDp2() == dp).toList();
            if (propertyIds.isEmpty()) {
                log.debug("[{}]: Could not find property for dp '{}' in thing '{}'", entity.getEntityID(), dp, entity.getTitle());
            } else {
                if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                    for (TuyaDeviceProperty propertyId : propertyIds) {
                        propertyId.getStateHandler().apply(rawValue);
                    }
                    return;
                }
                log.warn("[{}]: Could not update property '{}' with value {}. Datatype incompatible.",
                    entity.getEntityID(), propertyIds, rawValue);
            }
        }
    }

    private void addProperties(List<SchemaDp> schemaDps) {
        for (SchemaDp schemaDp : schemaDps) {
            properties.put(schemaDp.code, new TuyaDeviceProperty(schemaDp, entityContext, entity));
        }

        List<String> propertySuffixes = List.of("", "_1", "_2");
        List<String> switchProperties = List.of("switch_led", "led_switch");
        propertySuffixes.forEach(suffix -> switchProperties.forEach(property -> {
            TuyaDeviceProperty switchProperty = properties.get(property + suffix);
            if (switchProperty != null) {
                // remove switch property if brightness or color is present and add to dp2 instead
                TuyaDeviceProperty colourProperty = properties.get("colour_data" + suffix);
                TuyaDeviceProperty brightProperty = properties.get("bright_value" + suffix);
                boolean remove = false;

                if (colourProperty != null) {
                    colourProperty.setDp2(switchProperty.getDp());
                    remove = true;
                }
                if (brightProperty != null) {
                    brightProperty.setDp2(switchProperty.getDp());
                    remove = true;
                }

                if (remove) {
                    properties.remove(property + suffix);
                }
            }
        }));
    }

    public static void destroyAll() {
        udpDiscoveryListener.deactivate();
        eventLoopGroup.shutdownGracefully();
    }
}

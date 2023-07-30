package org.homio.addon.zigbee;

import static java.lang.String.format;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.z2m.Z2MEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.homio.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.api.exception.ProhibitedExecution;
import org.homio.api.model.Status;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.workspace.BroadcastLock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.homio.api.workspace.scratch.MenuBlock.StaticMenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ZigBeeBlocks extends Scratch3ExtensionBlocks {

    private final String ENDPOINT = "ENDPOINT";

    public static final String DEVICE__BASE_URL = "rest/device/";
    private final ServerMenuBlock deviceMenu;
    private final ServerMenuBlock deviceReadMenu;
    private final ServerMenuBlock deviceWriteMenu;
    private final ServerMenuBlock endpointMenu;
    private final ServerMenuBlock temperatureDeviceMenu;
    private final ServerMenuBlock humidityDeviceMenu;
    private final ServerMenuBlock readEndpointMenu;
    private final ServerMenuBlock writeEndpointMenu;
    private final StaticMenuBlock<OnOff> onOffMenu;
    private final ServerMenuBlock deviceWriteBoolMenu;
    private final ServerMenuBlock writeBoolEndpointMenu;
    private final String DEVICE = "DEVICE";

    public Scratch3ZigBeeBlocks(EntityContext entityContext, Z2MEntrypoint z2MEntrypoint) {
        super("#6d4747", entityContext, z2MEntrypoint, null);

        this.onOffMenu = menuStatic("onOffMenu", OnOff.class, OnOff.off);
        this.deviceMenu = menuServer("deviceMenu", DEVICE__BASE_URL + "device", "Device", "-");
        this.deviceReadMenu = menuServer("deviceReadMenu", DEVICE__BASE_URL + "device?access=read", "Device", "-");
        this.deviceWriteBoolMenu = menuServer("deviceWriteBoolMenu", DEVICE__BASE_URL + "device?access=write&type=bool", "Device", "-");
        this.deviceWriteMenu = menuServer("deviceWriteMenu", DEVICE__BASE_URL + "device?access=write", "Device", "-");
        this.temperatureDeviceMenu = menuServer("temperatureDeviceMenu", DEVICE__BASE_URL + "device/temperature", "Device", "-");
        this.humidityDeviceMenu = menuServer("humidityDeviceMenu", DEVICE__BASE_URL + "device/humidity", "Device", "-");
        this.endpointMenu = menuServer("endpointMenu", DEVICE__BASE_URL + "endpoints", "Endpoint", "-")
            .setDependency(this.deviceMenu);
        this.readEndpointMenu = menuServer("readEndpointMenu", DEVICE__BASE_URL + "endpoints?access=read", "Endpoint", "-")
            .setDependency(this.deviceReadMenu);
        this.writeEndpointMenu = menuServer("writeEndpointMenu", DEVICE__BASE_URL + "endpoints?access=write", "Endpoint", "-")
            .setDependency(this.deviceWriteMenu);
        this.writeBoolEndpointMenu = menuServer("writeBoolEndpointMenu", DEVICE__BASE_URL + "endpoints?access=write&type=bool", "Endpoint", "-")
            .setDependency(this.deviceWriteMenu);
        // reporter blocks
        blockReporter(50, "time_since_last_event", "time since last event [ENDPOINT] of [DEVICE]",
            workspaceBlock -> {
                Duration timeSinceLastEvent = getDeviceProperty(workspaceBlock).getTimeSinceLastEvent();
                return new DecimalType(timeSinceLastEvent.toSeconds()).setUnit("sec");
            }, block -> {
                block.addArgument(ENDPOINT, this.endpointMenu);
                block.addArgument(DEVICE, this.deviceMenu);
                block.appendSpace();
            });

        blockReporter(51, "value", "[ENDPOINT] value of [DEVICE]", this::getDevicePropertyState, block -> {
            block.addArgument(ENDPOINT, this.endpointMenu);
            block.addArgument(DEVICE, this.deviceMenu);
            block.overrideColor("#84185c");
        });

        blockReporter(52, "temperature", "temperature [DEVICE]",
            workspaceBlock -> getZigBeeProperty(workspaceBlock, deviceMenu, "temperature").getLastValue(),
            block -> {
                block.addArgument(DEVICE, this.temperatureDeviceMenu);
                block.overrideColor("#307596");
            });

        blockReporter(53, "humidity", "humidity [DEVICE]",
            workspaceBlock -> getZigBeeProperty(workspaceBlock, deviceMenu, "humidity").getLastValue(),
            block -> {
                block.addArgument(DEVICE, humidityDeviceMenu);
                block.overrideColor("#3B8774");
            });

        // command blocks
        blockCommand(80, "read_property", "Read [ENDPOINT] value of [DEVICE]", workspaceBlock ->
                getZigBeeProperty(workspaceBlock, deviceReadMenu, readEndpointMenu).readValue(),
            block -> {
                block.addArgument(ENDPOINT, readEndpointMenu);
                block.addArgument(DEVICE, deviceReadMenu);
            });

        blockCommand(81, "write_property", "Write [ENDPOINT] value [VALUE] of [DEVICE]", workspaceBlock -> {
            Object value = workspaceBlock.getInput(VALUE, true);
            getZigBeeProperty(workspaceBlock, deviceWriteMenu, writeEndpointMenu).writeValue(State.of(value));
        }, block -> {
            block.addArgument(ENDPOINT, writeEndpointMenu);
            block.addArgument(DEVICE, deviceWriteMenu);
            block.addArgument(VALUE, "-");
        });

        blockCommand(81, "write_bool", "Set [ENDPOINT] [VALUE] of [DEVICE]", workspaceBlock -> {
            OnOff value = workspaceBlock.getMenuValue(VALUE, this.onOffMenu);
            getZigBeeProperty(workspaceBlock, deviceWriteBoolMenu, writeBoolEndpointMenu).writeValue(OnOffType.of(value == OnOff.on));
        }, block -> {
            block.addArgument(ENDPOINT, writeBoolEndpointMenu);
            block.addArgument(DEVICE, deviceWriteBoolMenu);
            block.addArgument(VALUE, onOffMenu);
        });

        // hat blocks
        blockHat(90, "when_value_change", "When [ENDPOINT] of [DEVICE] changed", this::whenValueChange, block -> {
            block.addArgument(ENDPOINT, endpointMenu);
            block.addArgument(DEVICE, deviceMenu);
        });

        blockHat(91, "when_value_change_to", "When [ENDPOINT] of [DEVICE] changed to [VALUE]", this::whenValueChangeTo, block -> {
            block.addArgument(ENDPOINT, endpointMenu);
            block.addArgument(DEVICE, deviceMenu);
            block.addArgument(VALUE, "-");
        });

        blockHat(92, "when_no_value_change", "No changes [ENDPOINT] of [DEVICE] during [DURATION]sec.",
            this::whenNoValueChangeSince, block -> {
                block.addArgument(ENDPOINT, endpointMenu);
                block.addArgument(DEVICE, deviceMenu);
                block.addArgument("DURATION", 60);
            });
    }

    private DeviceEndpoint getZigBeeProperty(WorkspaceBlock workspaceBlock, ServerMenuBlock deviceMenu, ServerMenuBlock propertyMenu) {
        String propertyID = workspaceBlock.getMenuValue(ENDPOINT, propertyMenu);
        return getZigBeeProperty(workspaceBlock, deviceMenu, propertyID);
    }

    private void whenValueChangeTo(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            DeviceEndpoint property = getDeviceProperty(workspaceBlock);
            String value = workspaceBlock.getInputString(VALUE);
            if (StringUtils.isEmpty(value)) {
                workspaceBlock.logErrorAndThrow("Value must be not empty");
            }
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);

            property.addChangeListener(workspaceBlock.getId(), state -> {
                if (state.stringValue().equals(value)) {
                    lock.signalAll();
                }
            });
            workspaceBlock.onRelease(() -> property.removeChangeListener(workspaceBlock.getId()));
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    /**
     * Handler to wait specific seconds after some event and fire event after that
     */
    private void whenNoValueChangeSince(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            Integer secondsToWait = workspaceBlock.getInputInteger("DURATION");
            if (secondsToWait < 1) {
                workspaceBlock.logErrorAndThrow("Duration must be greater than 1 seconds. Value: {}", secondsToWait);
            }
            DeviceEndpoint property = getDeviceProperty(workspaceBlock);
            BroadcastLock eventOccurredLock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);

            // add listener on target property for any changes and wake up lock
            property.addChangeListener(workspaceBlock.getId(), state -> eventOccurredLock.signalAll());

            // thread context that will be started when property's listener fire event
            ThreadContext<Void> delayThread = entityContext.bgp().builder("when-no-val-" + workspaceBlock.getId())
                                                           .delay(Duration.ofSeconds(secondsToWait))
                                                           .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
                                                           .execute(next::handle, false);
            // remove listener from property. ThreadContext will be cancelled automatically
            workspaceBlock.onRelease(() ->
                property.removeChangeListener(workspaceBlock.getId()));
            // subscribe to lock that will restart delay thread after event
            workspaceBlock.subscribeToLock(eventOccurredLock, delayThread::reset);
        });
    }

    private void whenValueChange(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            DeviceEndpoint property = getDeviceProperty(workspaceBlock);
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);

            property.addChangeListener(workspaceBlock.getId(), state -> lock.signalAll());
            workspaceBlock.onRelease(() -> property.removeChangeListener(workspaceBlock.getId()));
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    private State getDevicePropertyState(WorkspaceBlock workspaceBlock) {
        return getDeviceProperty(workspaceBlock).getLastValue();
    }

    /**
     * return zigbee coordinator's device property. Return null if coordinator not ready
     */
    @NotNull
    private DeviceEndpoint getDeviceProperty(WorkspaceBlock workspaceBlock) {
        return getZigBeeProperty(workspaceBlock, deviceMenu, endpointMenu);
    }

    @NotNull
    private DeviceEndpoint getZigBeeProperty(WorkspaceBlock workspaceBlock, ServerMenuBlock deviceMenu, String propertyID) {
        String ieeeAddress = workspaceBlock.getMenuValue(DEVICE, deviceMenu);
        DeviceEndpoint property = getZigBeeProperty(ieeeAddress, propertyID);

        if (property == null) {
            // wait for property to be online at most 10 minutes
            BroadcastLock onlineStatus = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock,
                format("zigbee-%s-%s", ieeeAddress, propertyID), Status.ONLINE);
            if (onlineStatus.await(workspaceBlock, 10, TimeUnit.MINUTES)) {
                property = getZigBeeProperty(ieeeAddress, propertyID);
            }
        }
        if (property != null) {
            return property;
        }

        workspaceBlock.logErrorAndThrow("Unable to find zigbee property: {}/{}", ieeeAddress, propertyID);
        throw new ProhibitedExecution();
    }

    @Nullable
    private DeviceEndpoint getZigBeeProperty(String ieeeAddress, String propertyID) {
        for (ZigBeeBaseCoordinatorEntity coordinator : getZigBeeCoordinators()) {
            ZigBeeDeviceBaseEntity zigBeeDevice = coordinator.getZigBeeDevice(ieeeAddress);
            if (zigBeeDevice != null) {
                return zigBeeDevice.getDeviceEndpoint(propertyID);
            }
        }
        return null;
    }

    private Collection<ZigBeeBaseCoordinatorEntity> getZigBeeCoordinators() {
        Collection<ZigBeeBaseCoordinatorEntity> list = new ArrayList<>();

        for (MicroControllerBaseEntity microController : entityContext.findAll(MicroControllerBaseEntity.class)) {
            if (microController instanceof ZigBeeBaseCoordinatorEntity) {
                list.add((ZigBeeBaseCoordinatorEntity) microController);
            }
        }
        return list;
    }

    private enum OnOff {
        on, off
    }
}

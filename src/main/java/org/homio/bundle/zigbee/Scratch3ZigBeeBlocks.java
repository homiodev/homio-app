package org.homio.bundle.zigbee;

import static java.lang.String.format;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.entity.types.MicroControllerBaseEntity;
import org.homio.bundle.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.homio.bundle.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.bundle.api.entity.zigbee.ZigBeeProperty;
import org.homio.bundle.api.exception.ProhibitedExecution;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.state.DecimalType;
import org.homio.bundle.api.state.OnOffType;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.workspace.BroadcastLock;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.homio.bundle.api.workspace.scratch.MenuBlock.StaticMenuBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.bundle.z2m.Z2MEntrypoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ZigBeeBlocks extends Scratch3ExtensionBlocks {

    public static final String ZIGBEE__BASE_URL = "rest/zigbee/";
    public static final String ZIGBEE_CLUSTER_ID_URL = ZIGBEE__BASE_URL + "zcl/";
    public static final String ZIGBEE_CLUSTER_NAME_URL = ZIGBEE__BASE_URL + "clusterName/";
    public static final String ZIGBEE_MODEL_URL = ZIGBEE__BASE_URL + "model/";
    public static final String ZIGBEE_ALARM_URL = ZIGBEE__BASE_URL + "alarm";
    private static final String DEVICE = "Device";
    private final ServerMenuBlock deviceMenu;
    private final ServerMenuBlock deviceReadMenu;
    private final ServerMenuBlock deviceWriteMenu;
    private final ServerMenuBlock propertyMenu;
    private final ServerMenuBlock temperatureDeviceMenu;
    private final ServerMenuBlock humidityDeviceMenu;
    private final ServerMenuBlock readPropertyMenu;
    private final ServerMenuBlock writePropertyMenu;
    private final StaticMenuBlock<OnOff> onOffMenu;
    private final ServerMenuBlock deviceWriteBoolMenu;
    private final ServerMenuBlock writeBoolPropertyMenu;

    public Scratch3ZigBeeBlocks(EntityContext entityContext, Z2MEntrypoint z2MEntrypoint) {
        super("#6d4747", entityContext, z2MEntrypoint, null);

        this.onOffMenu = menuStatic("onOffMenu", OnOff.class, OnOff.off);
        this.deviceMenu = menuServer("deviceMenu", ZIGBEE__BASE_URL + "device", DEVICE, "-");
        this.deviceReadMenu = menuServer("deviceReadMenu", ZIGBEE__BASE_URL + "device?access=read", DEVICE, "-");
        this.deviceWriteBoolMenu = menuServer("deviceWriteBoolMenu", ZIGBEE__BASE_URL + "device?access=write&type=bool", DEVICE, "-");
        this.deviceWriteMenu = menuServer("deviceWriteMenu", ZIGBEE__BASE_URL + "device?access=write", DEVICE, "-");
        this.temperatureDeviceMenu = menuServer("temperatureDeviceMenu", ZIGBEE__BASE_URL + "device/temperature", DEVICE, "-");
        this.humidityDeviceMenu = menuServer("humidityDeviceMenu", ZIGBEE__BASE_URL + "device/humidity", DEVICE, "-");
        this.propertyMenu = menuServer("propertyMenu", ZIGBEE__BASE_URL + "property", "Property", "-")
            .setDependency(this.deviceMenu);
        this.readPropertyMenu = menuServer("readPropertyMenu", ZIGBEE__BASE_URL + "property?access=read", "Property", "-")
            .setDependency(this.deviceReadMenu);
        this.writePropertyMenu = menuServer("writePropertyMenu", ZIGBEE__BASE_URL + "property?access=write", "Property", "-")
            .setDependency(this.deviceWriteMenu);
        this.writeBoolPropertyMenu = menuServer("writeBoolPropertyMenu", ZIGBEE__BASE_URL + "property?access=write&type=bool", "Property", "-")
            .setDependency(this.deviceWriteMenu);
        // reporter blocks
        blockReporter(50, "time_since_last_event", "time since last event [PROPERTY] of [DEVICE]",
            workspaceBlock -> {
                Duration timeSinceLastEvent = getDeviceProperty(workspaceBlock).getTimeSinceLastEvent();
                return new DecimalType(timeSinceLastEvent.toSeconds()).setUnit("sec");
            }, block -> {
                block.addArgument("PROPERTY", this.propertyMenu);
                block.addArgument("DEVICE", this.deviceMenu);
                block.appendSpace();
            });

        blockReporter(51, "value", "[PROPERTY] value of [DEVICE]", this::getDevicePropertyState, block -> {
            block.addArgument("PROPERTY", this.propertyMenu);
            block.addArgument("DEVICE", this.deviceMenu);
            block.overrideColor("#84185c");
        });

        blockReporter(52, "temperature", "temperature [DEVICE]",
            workspaceBlock -> getZigBeeProperty(workspaceBlock, deviceMenu, "temperature").getLastValue(),
            block -> {
                block.addArgument("DEVICE", this.temperatureDeviceMenu);
                block.overrideColor("#307596");
            });

        blockReporter(53, "humidity", "humidity [DEVICE]",
            workspaceBlock -> getZigBeeProperty(workspaceBlock, deviceMenu, "humidity").getLastValue(),
            block -> {
                block.addArgument("DEVICE", humidityDeviceMenu);
                block.overrideColor("#3B8774");
            });

        // command blocks
        blockCommand(80, "read_property", "Read [PROPERTY] value of [DEVICE]", workspaceBlock -> {
            getZigBeeProperty(workspaceBlock, deviceReadMenu, readPropertyMenu).readValue();
        }, block -> {
            block.addArgument("PROPERTY", readPropertyMenu);
            block.addArgument("DEVICE", deviceReadMenu);
        });

        blockCommand(81, "write_property", "Write [PROPERTY] value [VALUE] of [DEVICE]", workspaceBlock -> {
            Object value = workspaceBlock.getInput(VALUE, true);
            getZigBeeProperty(workspaceBlock, deviceWriteMenu, writePropertyMenu).writeValue(State.of(value));
        }, block -> {
            block.addArgument("PROPERTY", writePropertyMenu);
            block.addArgument("DEVICE", deviceWriteMenu);
            block.addArgument(VALUE, "-");
        });

        blockCommand(81, "write_bool", "Set [PROPERTY] [VALUE] of [DEVICE]", workspaceBlock -> {
            OnOff value = workspaceBlock.getMenuValue(VALUE, this.onOffMenu);
            getZigBeeProperty(workspaceBlock, deviceWriteBoolMenu, writeBoolPropertyMenu).writeValue(OnOffType.of(value == OnOff.on));
        }, block -> {
            block.addArgument("PROPERTY", writeBoolPropertyMenu);
            block.addArgument("DEVICE", deviceWriteBoolMenu);
            block.addArgument(VALUE, onOffMenu);
        });

        // hat blocks
        blockHat(90, "when_value_change", "When [PROPERTY] of [DEVICE] changed", this::whenValueChange, block -> {
            block.addArgument("PROPERTY", propertyMenu);
            block.addArgument("DEVICE", deviceMenu);
        });

        blockHat(91, "when_value_change_to", "When [PROPERTY] of [DEVICE] changed to [VALUE]", this::whenValueChangeTo, block -> {
            block.addArgument("PROPERTY", propertyMenu);
            block.addArgument("DEVICE", deviceMenu);
            block.addArgument(VALUE, "-");
        });

        blockHat(92, "when_no_value_change", "No changes [PROPERTY] of [DEVICE] during [DURATION]sec.",
            this::whenNoValueChangeSince, block -> {
                block.addArgument("PROPERTY", propertyMenu);
                block.addArgument("DEVICE", deviceMenu);
                block.addArgument("DURATION", 60);
            });
    }

    private ZigBeeProperty getZigBeeProperty(WorkspaceBlock workspaceBlock, ServerMenuBlock deviceMenu, ServerMenuBlock propertyMenu) {
        String propertyID = workspaceBlock.getMenuValue("PROPERTY", propertyMenu);
        return getZigBeeProperty(workspaceBlock, deviceMenu, propertyID);
    }

    private void whenValueChangeTo(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            ZigBeeProperty property = getDeviceProperty(workspaceBlock);
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
            ZigBeeProperty property = getDeviceProperty(workspaceBlock);
            BroadcastLock eventOccurredLock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);

            // add listener on target property for any changes and wake up lock
            property.addChangeListener(workspaceBlock.getId(), state -> eventOccurredLock.signalAll());

            // thread context that will be started when property's listener fire event
            ThreadContext<Void> delayThread = entityContext.bgp().builder("when-no-val-" + workspaceBlock.getId())
                                                           .delay(Duration.ofSeconds(secondsToWait))
                                                           .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
                                                           .execute(next::handle, false);
            // remove listener from property. ThreadContext will be cancelled automatically
            workspaceBlock.onRelease(() -> {
                property.removeChangeListener(workspaceBlock.getId());
            });
            // subscribe to lock that will restart delay thread after event
            workspaceBlock.subscribeToLock(eventOccurredLock, delayThread::reset);
        });
    }

    private void whenValueChange(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            ZigBeeProperty property = getDeviceProperty(workspaceBlock);
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);

            property.addChangeListener(workspaceBlock.getId(), state -> {
                lock.signalAll();
            });
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
    private ZigBeeProperty getDeviceProperty(WorkspaceBlock workspaceBlock) {
        return getZigBeeProperty(workspaceBlock, deviceMenu, propertyMenu);
    }

    @NotNull
    private ZigBeeProperty getZigBeeProperty(WorkspaceBlock workspaceBlock, ServerMenuBlock deviceMenu, String propertyID) {
        String ieeeAddress = workspaceBlock.getMenuValue("DEVICE", deviceMenu);
        ZigBeeProperty property = getZigBeeProperty(ieeeAddress, propertyID);

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
    private ZigBeeProperty getZigBeeProperty(String ieeeAddress, String propertyID) {
        for (ZigBeeBaseCoordinatorEntity coordinator : getZigBeeCoordinators()) {
            ZigBeeDeviceBaseEntity zigBeeDevice = coordinator.getZigBeeDevice(ieeeAddress);
            if (zigBeeDevice != null) {
                return zigBeeDevice.getProperty(propertyID);
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

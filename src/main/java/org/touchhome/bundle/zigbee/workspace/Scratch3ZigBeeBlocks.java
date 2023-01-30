package org.touchhome.bundle.zigbee.workspace;

import static java.lang.String.format;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.WorkspaceBlockImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.entity.types.MicroControllerBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeProperty;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.state.QuantityType;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.z2m.Z2MEntrypoint;
import tech.units.indriya.unit.Units;

@Getter
@Component
public class Scratch3ZigBeeBlocks extends Scratch3ExtensionBlocks {

    public static final String ZIGBEE__BASE_URL = "rest/zigbee/";

    public static final String ZIGBEE_CLUSTER_ID_URL = ZIGBEE__BASE_URL + "zcl/";
    public static final String ZIGBEE_CLUSTER_NAME_URL = ZIGBEE__BASE_URL + "clusterName/";
    public static final String ZIGBEE_MODEL_URL = ZIGBEE__BASE_URL + "model/";
    public static final String ZIGBEE_ALARM_URL = ZIGBEE__BASE_URL + "alarm";

    private final ServerMenuBlock deviceMenu;
    private final ServerMenuBlock propertyMenu;

    public Scratch3ZigBeeBlocks(EntityContext entityContext, Z2MEntrypoint z2MEntrypoint) {
        super("#6d4747", entityContext, z2MEntrypoint, null);

        this.deviceMenu = menuServer("deviceMenu", ZIGBEE__BASE_URL + "device", "Button device", "-");
        this.propertyMenu = menuServer("propertyMenu", ZIGBEE__BASE_URL + "property", "Property", "-")
            .setDependency(this.deviceMenu);

        blockReporter(20, "time_since_last_event", "time since last event [PROPERTY] of [DEVICE]", this::timeSinceLastEvent, block -> {
            block.addArgument("PROPERTY", this.propertyMenu);
            block.addArgument("DEVICE", this.deviceMenu);
            block.appendSpace();
        });

        blockReporter(70, "value", "[PROPERTY] value of [DEVICE]", this::getDevicePropertyState, block -> {
            block.addArgument("PROPERTY", this.propertyMenu);
            block.addArgument("DEVICE", this.deviceMenu);
            block.overrideColor("#853139");
        });

        blockHat(90, "when_value_change", "When [PROPERTY] of [DEVICE] changed", this::whenValueChange, block -> {
            block.addArgument("PROPERTY", this.propertyMenu);
            block.addArgument("DEVICE", this.deviceMenu);
        });

        blockHat(100, "when_no_value_change", "No changes [PROPERTY] of [DEVICE] during [DURATION]sec.", this::whenNoValueChangeSince, block -> {
            block.addArgument("PROPERTY", this.propertyMenu);
            block.addArgument("DEVICE", this.deviceMenu);
            block.addArgument("DURATION", 60);
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

    private QuantityType timeSinceLastEvent(WorkspaceBlock workspaceBlock) {
        Duration timeSinceLastEvent = getDeviceProperty(workspaceBlock).getTimeSinceLastEvent();
        return new QuantityType(timeSinceLastEvent.toSeconds(), Units.SECOND);
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
    private ZigBeeProperty getDeviceProperty(WorkspaceBlock workspaceBlock) {
        String ieeeAddress = workspaceBlock.getMenuValue("DEVICE", this.deviceMenu);
        String propertyID = workspaceBlock.getMenuValue("PROPERTY", this.propertyMenu);
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
            ZigBeeProperty property = coordinator.getZigBeeDeviceProperty(ieeeAddress, propertyID);
            if (property != null) {
                return property;
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
}

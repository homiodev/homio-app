package org.touchhome.bundle.zigbee.workspace;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.zcl.ZclCommand;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Getter
@Component
@Scratch3Extension("zigbee")
public class Scratch3ZigBeeBlocks extends Scratch3ZigbeeExtensionBlocks {

    public static final String ZIGBEE__BASE_URL = "rest/zigbee/option/";

    public static final String ZIGBEE_CLUSTER_ID_URL = ZIGBEE__BASE_URL + "zcl/";
    public static final String ZIGBEE_CLUSTER_NAME_URL = ZIGBEE__BASE_URL + "clusterName/";
    public static final String ZIGBEE_MODEL_URL = ZIGBEE__BASE_URL + "model/";
    public static final String ZIGBEE_ALARM_URL = ZIGBEE__BASE_URL + "alarm";

    private final Scratch3Block timeSinceLastEvent;

    private final Scratch3Block whenEventReceived;
    private final BroadcastLockManager broadcastLockManager;
    private final ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener;
    private ZigBeeCoordinatorHandler coordinatorHandler;
    private Scratch3ZigBeeButtonsBlocks scratch3ZigBeeButtonsBlocks;

    public Scratch3ZigBeeBlocks(EntityContext entityContext, BroadcastLockManager broadcastLockManager, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        super("#6d4747", entityContext);
        this.broadcastLockManager = broadcastLockManager;
        this.zigBeeDeviceUpdateValueListener = zigBeeDeviceUpdateValueListener;
        this.entityContext.listenSettingValue(ZigbeeStatusSetting.class, status -> {
            if (status == DeviceStatus.ONLINE) {
                this.coordinatorHandler = this.entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
            } else {
                this.coordinatorHandler = null;
            }
        });

        this.scratch3ZigBeeButtonsBlocks = new Scratch3ZigBeeButtonsBlocks(entityContext, zigBeeDeviceUpdateValueListener);

        // Menu

        // Items
        this.whenEventReceived = Scratch3Block.ofHandler(10, "when_event_received", BlockType.hat, "when got [EVENT] event", this::whenEventReceivedHandler);
        this.whenEventReceived.addArgument(EVENT, ArgumentType.reference);

        this.timeSinceLastEvent = Scratch3Block.ofEvaluate(20, "time_since_last_event", BlockType.reporter, "time since last event [EVENT]", this::timeSinceLastEventEvaluate);
        this.timeSinceLastEvent.addArgument(EVENT, ArgumentType.reference);
        this.timeSinceLastEvent.appendSpace();

        this.postConstruct(this.scratch3ZigBeeButtonsBlocks);

        // descriptions
    }

    static void linkVariable(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener,
                             String varId, String description, WorkspaceBlock workspaceBlock, String key, MenuBlock.ServerMenuBlock menuBlock) {
        linkVariable(zigBeeDeviceUpdateValueListener, varId, description, workspaceBlock, key, menuBlock, menuBlock.getClusters()[0], null);
    }

    static void linkVariable(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener,
                             String varId, String description, WorkspaceBlock workspaceBlock, String key, MenuBlock.ServerMenuBlock menuBlock, Integer clusterID, String clusterName) {
        String ieeeAddress = workspaceBlock.getMenuValue(key, menuBlock, String.class);
        ZigBeeDeviceStateUUID zigBeeDeviceStateUUID = ZigBeeDeviceStateUUID.require(ieeeAddress, clusterID, null, clusterName);
        // listen from device and write to variable
        zigBeeDeviceUpdateValueListener.addLinkListener(zigBeeDeviceStateUUID, varId, description, state -> {
            WorkspaceVariableEntity workspaceVariableEntity = workspaceBlock.getEntityContext().getEntity(WorkspaceVariableEntity.PREFIX + varId);
            if (workspaceVariableEntity.getValue() != state.getState().floatValue()) {
                workspaceBlock.getEntityContext().save(workspaceVariableEntity.setValue(state.getState().floatValue()));
            }
        });
    }

    static ScratchDeviceState fetchValueFromDevice(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener, WorkspaceBlock workspaceBlock, Integer clusterID, String clusterName, String sensor, MenuBlock menuBlock) {
        return fetchValueFromDevice(zigBeeDeviceUpdateValueListener, workspaceBlock, new Integer[]{clusterID}, clusterName, sensor, menuBlock);
    }

    static ScratchDeviceState fetchValueFromDevice(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener, WorkspaceBlock workspaceBlock, Integer[] clusters, String sensor, MenuBlock menuBlock) {
        return fetchValueFromDevice(zigBeeDeviceUpdateValueListener, workspaceBlock, clusters, null, sensor, menuBlock);
    }

    static ScratchDeviceState fetchValueFromDevice(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener, WorkspaceBlock workspaceBlock, Integer[] clusters, String clusterName, String sensor, MenuBlock menuBlock) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, sensor, menuBlock);
        for (Integer clusterId : clusters) {
            ScratchDeviceState deviceState = zigBeeDeviceUpdateValueListener.getDeviceState(ZigBeeDeviceStateUUID.require(zigBeeDeviceEntity.getIeeeAddress(), clusterId, null, clusterName));
            if (deviceState != null) {
                return deviceState;
            }
        }

        return null;
    }

    public static void handleCommand(WorkspaceBlock workspaceBlock, ZigBeeDeviceEntity zigBeeDeviceEntity, ZigBeeBaseChannelConverter zigBeeBaseChannelConverter, ZclCommand zclCommand) {
        try {
            Future<CommandResult> result = zigBeeBaseChannelConverter.handleCommand(zclCommand);
            if (result != null) {
                CommandResult commandResult = result.get(10, TimeUnit.SECONDS);
                if (!commandResult.isSuccess()) {
                    workspaceBlock.logWarn("Send button command: <{}> to device: <{}> not success", zclCommand, zigBeeDeviceEntity.getIeeeAddress());
                }
            }
        } catch (Exception ex) {
            workspaceBlock.logError("Unable to execute command <{}>", zclCommand, ex);
        }
    }

    static float fetchFloat(ScratchDeviceState scratchDeviceState) {
        return scratchDeviceState == null ? 0 : scratchDeviceState.getState().floatValue();
    }

    static int fetchInt(ScratchDeviceState scratchDeviceState) {
        return scratchDeviceState == null ? 0 : scratchDeviceState.getState().intValue();
    }

    static ZigBeeDeviceEntity getZigBeeDevice(WorkspaceBlock workspaceBlock, String key, MenuBlock menuBlock) {
        return getZigBeeDevice(workspaceBlock, workspaceBlock.getMenuValue(key, menuBlock, String.class));
    }

    static ZigBeeDeviceEntity getZigBeeDevice(WorkspaceBlock workspaceBlock, String ieeeAddress) {
        if (ieeeAddress == null) {
            workspaceBlock.logErrorAndThrow("Unable to find ieeeAddress");
        }
        ZigBeeDeviceEntity entity = workspaceBlock.getEntityContext().getEntity(ZigBeeDeviceEntity.PREFIX + ieeeAddress);
        if (entity == null) {
            workspaceBlock.logErrorAndThrow("Unable to find ZigBee node with IEEEAddress: <{}>", ieeeAddress);
        }
        return entity;
    }

    private void handleWhenEventReceived(WorkspaceBlock workspaceBlock, ZigBeeEventHandler handler) {
        WorkspaceBlock workspaceEventBlock = workspaceBlock.getInputWorkspaceBlock("EVENT");

        Scratch3Block scratch3Block = this.getBlocksMap().get(workspaceEventBlock.getOpcode());
        Pair<String, MenuBlock> sensorMenuBlock = scratch3Block.findMenuBlock(k -> k.endsWith("_SENSOR"));
        Pair<String, MenuBlock> endpointMenuBlock = scratch3Block.findMenuBlock(s -> s.endsWith("_ENDPOINT"));

        WorkspaceBlock sensorMenuRef = workspaceEventBlock.getInputWorkspaceBlock(sensorMenuBlock.getKey());
        String ieeeAddress = sensorMenuRef.getField(sensorMenuBlock.getValue().getName());
        String endpointRef = null;

        if (endpointMenuBlock != null) {
            WorkspaceBlock endpointMenuRef = workspaceEventBlock.getInputWorkspaceBlock(endpointMenuBlock.getKey());
            endpointRef = endpointMenuRef.getField(endpointMenuBlock.getValue().getName());
        }

        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, ieeeAddress);
        if (zigBeeDeviceEntity == null) {
            throw new IllegalStateException("Unable to find Zigbee device entity <" + ieeeAddress + ">");
        }

        BroadcastLock<ScratchDeviceState> lock = broadcastLockManager.getOrCreateLock(workspaceBlock.getId());
        boolean availableReceiveEvents = false;

        if (scratch3Block instanceof Scratch3ZigbeeBlock) {
            for (Scratch3ZigbeeBlock.ZigBeeEventHandler eventConsumer : ((Scratch3ZigbeeBlock) scratch3Block).getEventConsumers()) {
                availableReceiveEvents = true;
                eventConsumer.handle(ieeeAddress, endpointRef, state -> lock.signalAll());
            }
        }

        Integer[] clusters = ((MenuBlock.ServerMenuBlock) sensorMenuBlock.getValue()).getClusters();
        if (clusters != null) {
            availableReceiveEvents = true;
            addZigbeeEventListener(ieeeAddress, clusters, null, lock::signalAll);
        }

        if (!availableReceiveEvents) {
            throw new IllegalStateException("Unable to find event listener");
        }

        while (!Thread.currentThread().isInterrupted()) {
            if (lock.await(workspaceBlock)) {
                handler.handle(zigBeeDeviceEntity, lock.getValue());
            }
        }
    }

    private void whenEventReceivedHandler(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock substack = workspaceBlock.getNext();
        if (substack != null) {
            this.handleWhenEventReceived(workspaceBlock, (zigBeeDeviceEntity, ignore) -> substack.handle());
        }
    }

    private long timeSinceLastEventEvaluate(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock workspaceEventBlock = workspaceBlock.getInputWorkspaceBlock("EVENT");

        Scratch3Block scratch3Block = this.getBlocksMap().get(workspaceEventBlock.getOpcode());
        Pair<String, MenuBlock> menuBlock = scratch3Block.findMenuBlock(k -> k.endsWith("_SENSOR"));

        Integer[] clusters = ((MenuBlock.ServerMenuBlock) menuBlock.getValue()).getClusters();

        ScratchDeviceState scratchDeviceState = fetchValueFromDevice(zigBeeDeviceUpdateValueListener,
                workspaceEventBlock, clusters, menuBlock.getKey(), menuBlock.getValue());
        if (scratchDeviceState != null) {
            return (System.currentTimeMillis() - scratchDeviceState.getDate()) / 1000;
        }
        return Long.MAX_VALUE;
    }

    private void addZigbeeEventListener(String nodeIEEEAddress, Integer[] clusters, Integer endpoint, Consumer<ScratchDeviceState> consumer) {
        for (Integer clusterId : clusters) {
            ZigBeeDeviceStateUUID zigBeeDeviceStateUUID = ZigBeeDeviceStateUUID.require(nodeIEEEAddress, clusterId, endpoint, null);
            this.zigBeeDeviceUpdateValueListener.addListener(zigBeeDeviceStateUUID, consumer);
        }
    }

    private interface ZigBeeEventHandler {
        void handle(ZigBeeDeviceEntity zigBeeDeviceEntity, ScratchDeviceState scratchDeviceState);
    }
}

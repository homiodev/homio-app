package org.touchhome.bundle.xaomi.workspace;

import com.zsmartsystems.zigbee.zcl.clusters.ZclAnalogInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclMultistateInputBasicCluster;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;
import org.touchhome.bundle.zigbee.workspace.ScratchDeviceState;
import org.touchhome.bundle.zigbee.workspace.ZigBeeDeviceUpdateValueListener;

import java.util.function.Consumer;

import static org.touchhome.bundle.xaomi.workspace.MagicCubeHandler.*;
import static org.touchhome.bundle.zigbee.workspace.Scratch3ZigBeeBlocks.ZIGBEE_MODEL_URL;

@Getter
@Component
@Scratch3Extension("xaomi")
public class Scratch3XaomiBlocks extends Scratch3ExtensionBlocks {

    private static final String CUBE_MODE_IDENTIFIER = "lumi.sensor_cube";

    private static final String CUBE_SENSOR = "CUBE_SENSOR";

    private final Scratch3Block magicCubeEvent;
    private final MenuBlock.StaticMenuBlock cubeEventMenu;
    private final MenuBlock.ServerMenuBlock cubeSensorMenu;
    private final Scratch3Block magicCubeLastValue;
    private final BroadcastLockManager broadcastLockManager;
    private final ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener;
    private ZigBeeCoordinatorHandler coordinatorHandler;

    public Scratch3XaomiBlocks(EntityContext entityContext, BroadcastLockManager broadcastLockManager,
                               ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        super("#856d21", entityContext);
        this.broadcastLockManager = broadcastLockManager;
        this.zigBeeDeviceUpdateValueListener = zigBeeDeviceUpdateValueListener;
        this.entityContext.listenSettingValue(ZigbeeStatusSetting.class, status -> {
            if (status == DeviceStatus.ONLINE) {
                this.coordinatorHandler = this.entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
            } else {
                this.coordinatorHandler = null;
            }
        });

        this.cubeEventMenu = MenuBlock.ofStatic("cubeEventMenu", MagicCubeEvent.class);
        this.cubeEventMenu.subMenu(MagicCubeEvent.MOVE, MoveSide.class);
        this.cubeEventMenu.subMenu(MagicCubeEvent.TAP_TWICE, TapSide.class);

        this.cubeSensorMenu = MenuBlock.ofServer("cubeSensorMenu", ZIGBEE_MODEL_URL + CUBE_MODE_IDENTIFIER, "Magic Cube", "-");

        this.magicCubeEvent = Scratch3Block.ofHandler(1, "when_cube_event", BlockType.hat,
                "Cube [CUBE_SENSOR] event [EVENT]", this::magicCubeEventHandler);
        this.magicCubeEvent.addArgumentServerSelection(CUBE_SENSOR, this.cubeSensorMenu);
        this.magicCubeEvent.addArgument(EVENT, ArgumentType.string, MagicCubeEvent.ANY_EVENT, this.cubeEventMenu);

        this.magicCubeLastValue = Scratch3Block.ofEvaluate(2, "cube_value", BlockType.reporter, "Cube [CUBE_SENSOR] last value [EVENT]", this::cubeLastValueEvaluate);
        this.magicCubeLastValue.addArgumentServerSelection(CUBE_SENSOR, this.cubeSensorMenu);
        this.magicCubeLastValue.addArgument(EVENT, ArgumentType.string, MagicCubeEvent.ANY_EVENT, this.cubeEventMenu);

        this.postConstruct();

        zigBeeDeviceUpdateValueListener.addDescribeHandlerByModel(CUBE_MODE_IDENTIFIER, (state) -> "Magic cube <" + new CubeValueDescriptor(state) + ">", false);
    }

    private Object cubeLastValueEvaluate(WorkspaceBlock workspaceBlock) {
        String ieeeAddress = fetchIEEEAddress(workspaceBlock);
        ScratchDeviceState deviceState = this.zigBeeDeviceUpdateValueListener.getDeviceState(
                ZigBeeDeviceStateUUID.require(ieeeAddress, ZclMultistateInputBasicCluster.CLUSTER_ID, null, null),
                ZigBeeDeviceStateUUID.require(ieeeAddress, ZclAnalogInputBasicCluster.CLUSTER_ID, null, null));
        if (deviceState != null) {
            return deviceState.getState().floatValue();
        }
        return null;
    }

    private void magicCubeEventHandler(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock substack = workspaceBlock.getNext();
        if (substack == null) {
            workspaceBlock.logErrorAndThrow("No next block found");
            return;
        }
        String expectedMenuValueStr = workspaceBlock.getMenuValue(EVENT, this.cubeEventMenu, String.class);
        MagicCubeEvent expectedMenuValue = MagicCubeEvent.getEvent(expectedMenuValueStr);
        final TapSide tapSide = expectedMenuValue == MagicCubeEvent.TAP_TWICE ? TapSide.valueOf(expectedMenuValueStr) : null;
        final MoveSide moveSide = expectedMenuValue == MagicCubeEvent.MOVE ? MoveSide.valueOf(expectedMenuValueStr) : null;

        String ieeeAddress = fetchIEEEAddress(workspaceBlock);
        if (ieeeAddress == null) {
            return;
        }

        BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock.getId());
        Consumer<ScratchDeviceState> consumer = sds -> {
            CubeValueDescriptor cubeValueDescriptor = new CubeValueDescriptor(sds);
            if (cubeValueDescriptor.match(expectedMenuValue, tapSide, moveSide)) {
                lock.signalAll();
            }
        };

        addZigbeeEventListener(ieeeAddress, ZclMultistateInputBasicCluster.CLUSTER_ID, consumer);
        addZigbeeEventListener(ieeeAddress, ZclAnalogInputBasicCluster.CLUSTER_ID, consumer);

        while (!Thread.currentThread().isInterrupted()) {
            if (lock.await(workspaceBlock)) {
                substack.handle();
            }
        }
    }

    private String fetchIEEEAddress(WorkspaceBlock workspaceBlock) {
        String ieeeAddress = workspaceBlock.getMenuValue(CUBE_SENSOR, this.cubeSensorMenu, String.class);
        ZigBeeDeviceEntity device = entityContext.getEntity(ZigBeeDeviceEntity
                .PREFIX + ieeeAddress);
        if (device == null) {
            workspaceBlock.logErrorAndThrow("Unable to find Magic cube entity: <{}>", ieeeAddress);
        }
        return ieeeAddress;
    }

    private void addZigbeeEventListener(String nodeIEEEAddress, int clusterId, Consumer<ScratchDeviceState> consumer) {
        ZigBeeDeviceStateUUID zigBeeDeviceStateUUID = ZigBeeDeviceStateUUID.require(nodeIEEEAddress, clusterId, null, null);
        this.zigBeeDeviceUpdateValueListener.addListener(zigBeeDeviceStateUUID, consumer);
    }
}

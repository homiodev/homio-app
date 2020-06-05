package org.touchhome.bundle.zigbee.workspace;

import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OffCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ToggleCommand;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.ZclOnOffCommand;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.util.UpdatableValue;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.ZigBeeNodeDescription;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.touchhome.bundle.zigbee.workspace.Scratch3ZigBeeBlocks.*;

@Getter
final class Scratch3ZigBeeButtonsBlocks {

    private static final String BUTTON_ENDPOINT = "BUTTON_ENDPOINT";
    private static final String DOUBLE_BUTTON_SENSOR = "DOUBLE_BUTTON_SENSOR";
    private static final String BUTTON_SENSOR = "BUTTON_SENSOR";
    private static final String BUTTON_SIGNAL = "BUTTON_SIGNAL";

    private final MenuBlock.ServerMenuBlock buttonSensorMenu;
    private final MenuBlock.ServerMenuBlock doubleButtonSensorMenu;
    private final MenuBlock.StaticMenuBlock buttonSendSignalValueMenu;
    private final MenuBlock.StaticMenuBlock buttonEndpointValueMenu;
    private final MenuBlock.StaticMenuBlock buttonEndpointGetterValueMenu;

    private final Scratch3Block turnOnOfButtonCommand;
    private final Scratch3Block turnOnOf2XButtonCommand;
    private final Scratch3ZigbeeBlock buttonStatus;
    private final Scratch3ZigbeeBlock button2XStatus;

    private final ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener;
    private final EntityContext entityContext;

    private Map<String, UpdatableValue<Boolean>> statelessButtonStates = new HashMap<>();

    Scratch3ZigBeeButtonsBlocks(EntityContext entityContext, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        this.entityContext = entityContext;
        this.zigBeeDeviceUpdateValueListener = zigBeeDeviceUpdateValueListener;

        this.buttonSensorMenu = MenuBlock.ofServer("buttonSensorMenu", ZIGBEE__BASE_URL + "buttons", "Button Sensor", "-", ZclOnOffCluster.CLUSTER_ID);
        this.doubleButtonSensorMenu = MenuBlock.ofServer("doubleButtonSensorMenu", ZIGBEE__BASE_URL + "doubleButtons", "Button Sensor", "-");

        this.buttonSendSignalValueMenu = MenuBlock.ofStatic("buttonSendSignalValueMenu", ButtonFireSignal.class);
        this.buttonEndpointValueMenu = MenuBlock.ofStatic("buttonEndpointValueMenu", ButtonEndpoint.class);
        this.buttonEndpointGetterValueMenu = MenuBlock.ofStatic("buttonEndpointGetterValueMenu", ButtonEndpointGetter.class);

        this.buttonStatus = Scratch3Block.ofEvaluate(70, "button_value", BlockType.reporter, "button value [BUTTON_SENSOR]", this::buttonStatusEvaluate, Scratch3ZigbeeBlock.class);
        this.buttonStatus.addArgumentServerSelection(BUTTON_SENSOR, this.buttonSensorMenu);
        this.buttonStatus.overrideColor("#853139");
        this.buttonStatus.addZigBeeEventHandler((ieeeAddress, endpointRef, consumer) -> zigBeeDeviceUpdateValueListener.addModelIdentifierListener("lumi.remote", consumer));
        this.buttonStatus.allowLinkBoolean((varId, workspaceBlock) -> {
            ZigBeeDeviceEntity zigBeeDevice = getZigBeeDevice(workspaceBlock, BUTTON_SENSOR, buttonSensorMenu);
            allowButtonLinkBoolean(zigBeeDeviceUpdateValueListener, varId, workspaceBlock, zigBeeDevice.getIeeeAddress(), null);
        });
        this.buttonStatus.setZigBeeLinkGenerator((endpoint, zigBeeDevice, varGroup, varName) ->
                this.buttonStatus.codeGenerator("zigbee")
                        .setMenu(this.buttonSensorMenu, zigBeeDevice.getNodeIeeeAddress())
                        .generateBooleanLink(varGroup, varName, entityContext), ZclOnOffCluster.CLUSTER_ID, 1);

        this.turnOnOfButtonCommand = Scratch3Block.ofHandler(90, "button_turn_on_off_button", BlockType.command, "turn [BUTTON_SIGNAL] button [BUTTON_SENSOR]", this::turnOnOffButtonHandler);
        this.turnOnOfButtonCommand.addArgumentServerSelection(BUTTON_SENSOR, this.buttonSensorMenu);
        this.turnOnOfButtonCommand.addArgument(BUTTON_SIGNAL, ArgumentType.string, ButtonFireSignal.on, this.buttonSendSignalValueMenu);
        this.turnOnOfButtonCommand.appendSpace();
        this.turnOnOfButtonCommand.overrideColor("#853139");

        this.button2XStatus = Scratch3Block.ofEvaluate(100, "double_button_value", BlockType.reporter,
                "button [BUTTON_ENDPOINT] value [DOUBLE_BUTTON_SENSOR]", this::doubleButtonStatusEvaluate, Scratch3ZigbeeBlock.class);
        this.button2XStatus.addArgumentServerSelection(DOUBLE_BUTTON_SENSOR, this.doubleButtonSensorMenu);
        this.button2XStatus.addArgument(BUTTON_ENDPOINT, ArgumentType.string, ButtonEndpoint.Left, this.buttonEndpointGetterValueMenu);
        this.button2XStatus.overrideColor("#A70F1D");
        this.button2XStatus.addZigBeeEventHandler((ieeeAddress, endpointRef, consumer) -> {
            ButtonEndpointGetter buttonEndpoint = ButtonEndpointGetter.valueOf(endpointRef);
            zigBeeDeviceUpdateValueListener.addListener(ZigBeeDeviceStateUUID.require(ieeeAddress, ZclOnOffCluster.CLUSTER_ID, buttonEndpoint.value, null), consumer);
        });
        // add link boolean value
        this.button2XStatus.allowLinkBoolean((varId, workspaceBlock) -> {
            ZigBeeDeviceEntity zigBeeDevice = getZigBeeDevice(workspaceBlock, DOUBLE_BUTTON_SENSOR, doubleButtonSensorMenu);
            ButtonEndpoint buttonEndpoint = workspaceBlock.getMenuValue(BUTTON_ENDPOINT, this.buttonEndpointGetterValueMenu, ButtonEndpoint.class);
            allowButtonLinkBoolean(zigBeeDeviceUpdateValueListener, varId, workspaceBlock, zigBeeDevice.getIeeeAddress(), buttonEndpoint);
        });
        this.button2XStatus.setZigBeeLinkGenerator((endpoint, zigBeeDevice, varGroup, varName) ->
                this.button2XStatus.codeGenerator("zigbee")
                        .setMenu(this.buttonEndpointGetterValueMenu, ButtonEndpoint.of(endpoint.getEndpointId()))
                        .setMenu(this.doubleButtonSensorMenu, zigBeeDevice.getNodeIeeeAddress())
                        .generateBooleanLink(varGroup, varName, entityContext), ZclOnOffCluster.CLUSTER_ID, 2);

        this.turnOnOf2XButtonCommand = Scratch3Block.ofHandler(110, "double_button_turn_on_off_button",
                BlockType.command, "turn [BUTTON_SIGNAL] [BUTTON_ENDPOINT] button [DOUBLE_BUTTON_SENSOR]", this::turnOnOffDoubleButtonHandler);
        this.turnOnOf2XButtonCommand.addArgumentServerSelection(DOUBLE_BUTTON_SENSOR, this.doubleButtonSensorMenu);
        this.turnOnOf2XButtonCommand.addArgument(BUTTON_SIGNAL, ArgumentType.string, ButtonFireSignal.on, this.buttonSendSignalValueMenu);
        this.turnOnOf2XButtonCommand.addArgument(BUTTON_ENDPOINT, ArgumentType.string, ButtonEndpoint.Left, this.buttonEndpointValueMenu);
        this.turnOnOf2XButtonCommand.appendSpace();
        this.turnOnOf2XButtonCommand.overrideColor("#A70F1D");

        // add descriptions
        this.addDescriptions(zigBeeDeviceUpdateValueListener);
    }

    private void turnOnOffButtonHandler(WorkspaceBlock workspaceBlock) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, BUTTON_SENSOR, buttonSensorMenu);
        ButtonFireSignal buttonSignal = getButtonFireSignal(workspaceBlock);
        switchButton(workspaceBlock, buttonSignal, zigBeeDeviceEntity, null);
    }

    private void turnOnOffDoubleButtonHandler(WorkspaceBlock workspaceBlock) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, DOUBLE_BUTTON_SENSOR, doubleButtonSensorMenu);
        ButtonFireSignal buttonSignal = getButtonFireSignal(workspaceBlock);
        ButtonEndpoint buttonEndpoint = workspaceBlock.getMenuValue(BUTTON_ENDPOINT, this.buttonEndpointValueMenu, ButtonEndpoint.class);
        switchButton(workspaceBlock, buttonSignal, zigBeeDeviceEntity, buttonEndpoint);
    }

    private ButtonFireSignal getButtonFireSignal(WorkspaceBlock workspaceBlock) {
        ButtonFireSignal buttonSignal;
        try {
            buttonSignal = workspaceBlock.getMenuValue(BUTTON_SIGNAL, this.buttonSendSignalValueMenu, ButtonFireSignal.class);
        } catch (Exception ex) {
            buttonSignal = workspaceBlock.getInputBoolean(BUTTON_SIGNAL) ? ButtonFireSignal.on : ButtonFireSignal.off;
        }
        return buttonSignal;
    }

    @SneakyThrows
    private void switchButton(WorkspaceBlock workspaceBlock, ButtonFireSignal buttonFireSignal, ZigBeeDeviceEntity zigBeeDeviceEntity, ButtonEndpoint buttonEndpoint) {
        ZclOnOffCommand zclOnOffCommand = buttonFireSignal.zclOnOffCommand.newInstance();
        workspaceBlock.logInfo("Switch button {}", zclOnOffCommand.getClass().getSimpleName());
        ZigBeeNodeDescription zigBeeNodeDescription = zigBeeDeviceEntity.getZigBeeNodeDescription();

        if (zigBeeNodeDescription == null) {
            workspaceBlock.logErrorAndThrow("Unable to switch button. Node not discovered");
            return;
        } else if (zigBeeNodeDescription.getChannels() == null) {
            workspaceBlock.logErrorAndThrow("Unable to switch button. Node has no zigbeeRequireEndpoints at all");
            return;
        }

        List<ZigBeeNodeDescription.ChannelDescription> onOffChannels = zigBeeNodeDescription.getChannels()
                .stream().filter(s -> s.getChannelUUID().getClusterId() == ZclOnOffCluster.CLUSTER_ID).collect(Collectors.toList());

        if (onOffChannels.isEmpty()) {
            workspaceBlock.logErrorAndThrow("Unable to find channel with On/Off ability for device: " + zigBeeDeviceEntity);
        } else if (buttonEndpoint != null && buttonEndpoint.value != null) {
            onOffChannels = onOffChannels.stream().filter(c -> c.getChannelUUID().getEndpointId().equals(buttonEndpoint.value)).collect(Collectors.toList());
        }

        if (onOffChannels.isEmpty()) {
            workspaceBlock.logErrorAndThrow("Unable to find channel with On/Off ability for device: " + zigBeeDeviceEntity);
        }

        for (ZigBeeNodeDescription.ChannelDescription onOffChannel : onOffChannels) {
            ZigBeeDeviceStateUUID uuid = onOffChannel.getChannelUUID();
            ZigBeeBaseChannelConverter beeBaseChannelConverter = zigBeeDeviceEntity.getZigBeeDevice().getZigBeeConverterEndpoints()
                    .get(new ZigBeeConverterEndpoint(uuid.getIeeeAddress(), uuid.getClusterId(), uuid.getEndpointId(), uuid.getClusterName()));
            handleCommand(workspaceBlock, zigBeeDeviceEntity, beeBaseChannelConverter, zclOnOffCommand);
        }
    }

    private int buttonStatusEvaluate(WorkspaceBlock workspaceBlock) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, BUTTON_SENSOR, buttonSensorMenu);
        if (statelessButtonStates.containsKey(zigBeeDeviceEntity.getIeeeAddress())) {
            return statelessButtonStates.get(zigBeeDeviceEntity.getIeeeAddress()).getValue() == Boolean.TRUE ? 1 : 0;
        }

        for (ZigBeeNodeDescription.ChannelDescription channel : zigBeeDeviceEntity.getZigBeeNodeDescription().getChannels()) {
            ScratchDeviceState scratchDeviceState = this.zigBeeDeviceUpdateValueListener.getDeviceState(channel.getChannelUUID());
            if (scratchDeviceState != null) {
                return scratchDeviceState.getState().intValue();
            }
        }
        return 0;
    }

    private int doubleButtonStatusEvaluate(WorkspaceBlock workspaceBlock) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = getZigBeeDevice(workspaceBlock, DOUBLE_BUTTON_SENSOR, doubleButtonSensorMenu);
        for (ZigBeeNodeDescription.ChannelDescription channel : zigBeeDeviceEntity.getZigBeeNodeDescription().getChannels()) {
            ScratchDeviceState scratchDeviceState = this.zigBeeDeviceUpdateValueListener.getDeviceState(channel.getChannelUUID());
            if (scratchDeviceState != null) {
                return scratchDeviceState.getState().intValue();
            }
        }
        return 0;
    }

    private void addDescriptions(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        zigBeeDeviceUpdateValueListener.addDescribeHandlerByModel("lumi.remote", state -> "Remote button", true);
        zigBeeDeviceUpdateValueListener.addModelIdentifierListener("lumi.remote", state -> {
            String ieeeAddress = state.getZigBeeDevice().getNodeIeeeAddress().toString();
            if (!statelessButtonStates.containsKey(ieeeAddress)) {
                statelessButtonStates.put(ieeeAddress, UpdatableValue.wrap(Boolean.FALSE, ieeeAddress + "_state"));
            }
            statelessButtonStates.get(ieeeAddress).update(!statelessButtonStates.get(ieeeAddress).getValue());
        });
        zigBeeDeviceUpdateValueListener.addDescribeHandlerByClusterId(ZclOnOffCluster.CLUSTER_ID, state -> {
            String btnIndex = "";
            if (state.getZigBeeDevice().getChannelCount(ZclOnOffCluster.CLUSTER_ID) == 2) {
                int endpointId = state.getUuid().getEndpointId();
                btnIndex = endpointId == 1 ? "Left " : endpointId == 2 ? "Right " : "Undefined ";
            }
            return "Button " + btnIndex + "click: " + state.getState();
        }, false);
    }

    private void allowButtonLinkBoolean(ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener, String varId, WorkspaceBlock workspaceBlock, String ieeeAddress, ButtonEndpoint buttonEndpoint) {
        ZigBeeDeviceStateUUID zigBeeDeviceStateUUID = ZigBeeDeviceStateUUID.require(ieeeAddress, ZclOnOffCluster.CLUSTER_ID, buttonEndpoint == null ? null : buttonEndpoint.value, null);
        // listen from device and write to variable
        zigBeeDeviceUpdateValueListener.addLinkListener(zigBeeDeviceStateUUID, varId, "On/Off" + (buttonEndpoint == null ? "" : " " + buttonEndpoint.name()), state -> {
            WorkspaceBooleanEntity workspaceBooleanEntity = workspaceBlock.getEntityContext().getEntity(WorkspaceBooleanEntity.PREFIX + varId);
            if (workspaceBooleanEntity.getValue() != state.getState().boolValue()) {
                workspaceBlock.getEntityContext().save(workspaceBooleanEntity.inverseValue());
            }
        });
        // listen boolean variable and fire events to device
        workspaceBlock.getEntityContext().addEntityUpdateListener(WorkspaceBooleanEntity.PREFIX + varId, (Consumer<WorkspaceBooleanEntity>) workspaceBooleanEntity -> {
            Boolean val = workspaceBooleanEntity.getValue();
            ScratchDeviceState deviceState = zigBeeDeviceUpdateValueListener.getDeviceState(zigBeeDeviceStateUUID);
            if (deviceState == null || deviceState.getState().boolValue() != val) {
                switchButton(workspaceBlock, ButtonFireSignal.of(val), getZigBeeDevice(workspaceBlock, ieeeAddress), buttonEndpoint);
            }
        });
    }

    @RequiredArgsConstructor
    private enum ButtonFireSignal {
        on(OnCommand.class), off(OffCommand.class), Toggle(ToggleCommand.class);

        private final Class<? extends ZclOnOffCommand> zclOnOffCommand;

        public static ButtonFireSignal of(boolean on) {
            return on ? ButtonFireSignal.on : ButtonFireSignal.off;
        }
    }

    @RequiredArgsConstructor
    private enum ButtonEndpoint {
        Left(1), Right(2), Both(null);
        private final Integer value;

        public static ButtonEndpoint of(int endpointId) {
            for (ButtonEndpoint value : ButtonEndpoint.values()) {
                if (value.value == endpointId) {
                    return value;
                }
            }
            throw new RuntimeException("Unable to find Button endpoint");
        }
    }

    @RequiredArgsConstructor
    private enum ButtonEndpointGetter {
        Left(1), Right(2), Any(null);
        private final Integer value;
    }
}

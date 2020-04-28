package org.touchhome.bundle.zigbee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.console.UIHeaderSettingAction;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorMatch;
import org.touchhome.bundle.api.ui.method.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.method.UIMethodAction;
import org.touchhome.bundle.zigbee.model.State;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.requireEndpoint.ZigbeeRequireEndpoints;
import org.touchhome.bundle.zigbee.setting.ZigbeeDiscoveryButtonSetting;
import org.touchhome.bundle.zigbee.workspace.ZigBeeDeviceUpdateValueListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ZigBeeConsolePlugin implements ConsolePlugin {

    private final ZigBeeBundleContext zigbeeBundleContext;
    private final EntityContext entityContext;

    @Override
    public int order() {
        return 500;
    }

    @Override
    public List<? extends HasEntityIdentifier> drawEntity() {
        List<ZigbeeConsoleDescription> res = new ArrayList<>();
        for (ZigBeeDevice zigBeeDevice : zigbeeBundleContext.getCoordinatorHandlers().getZigBeeDevices().values()) {
            ZigBeeNodeDescription desc = zigBeeDevice.getZigBeeNodeDescription();
            BaseEntity entity = entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + zigBeeDevice.getNodeIeeeAddress().toString());
            res.add(new ZigbeeConsoleDescription(
                    entity.getName(),
                    zigBeeDevice.getNodeIeeeAddress().toString(),
                    desc.getDeviceStatus(),
                    desc.getDeviceStatusMessage(),
                    desc.getModelIdentifier(),
                    desc.getFetchInfoStatus(),
                    !zigBeeDevice.getZigBeeConverterEndpoints().isEmpty(),
                    zigBeeDevice.getZigBeeNodeDescription().isNodeInitialized() && !zigBeeDevice.getZigBeeConverterEndpoints().isEmpty(),
                    entity.getEntityID()
            ));
        }
        return res;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @UIHeaderSettingAction(name = "zigbee.start_discovery", setting = ZigbeeDiscoveryButtonSetting.class)
    private static class ZigbeeConsoleDescription implements HasEntityIdentifier {

        @UIField(order = 1, inlineEdit = true)
        private String name;

        @UIField(order = 1)
        private String ieeeAddress;

        @UIField(order = 2)
        @UIFieldColorMatch(value = "ONLINE", color = "#1F8D2D")
        @UIFieldColorMatch(value = "OFFLINE", color = "#B22020")
        @UIFieldColorMatch(value = "UNKNOWN", color = "#818744")
        private DeviceStatus deviceStatus;

        @UIField(order = 3, color = "#B22020")
        private String errorMessage;

        @UIField(order = 4)
        @UIFieldSelectValueOnEmpty(label = "zigbee.action.selectModelIdentifier", color = "#A7D21E", method = "selectModelIdentifier")
        private String model;

        @UIField(order = 5)
        private ZigBeeNodeDescription.FetchInfoStatus fetchInfoStatus;

        @UIField(order = 6)
        @UIFieldColorMatch(value = "true", color = "#1F8D2D")
        @UIFieldColorMatch(value = "false", color = "#B22020")
        private boolean channelsInitialized;

        @UIField(order = 7)
        @UIFieldColorMatch(value = "true", color = "#1F8D2D")
        @UIFieldColorMatch(value = "false", color = "#B22020")
        private boolean initialized;

        private String entityID;

        @UIMethodAction(name = "ACTION.INITIALIZE_ZIGBEE_NODE")
        public String initializeZigbeeNode(ZigBeeDeviceEntity zigBeeDeviceEntity) {
            return zigBeeDeviceEntity.initializeZigbeeNode();
        }

        @UIMethodAction(name = "ACTION.SHOW_NODE_DESCRIPTION", responseAction = UIMethodAction.ResponseAction.ShowJson)
        public ZigBeeNodeDescription showNodeDescription(ZigBeeDeviceEntity zigBeeDeviceEntity) {
            return zigBeeDeviceEntity.getZigBeeNodeDescription();
        }

        @UIMethodAction(name = "ACTION.SHOW_LAST_VALUES", responseAction = UIMethodAction.ResponseAction.ShowJson)
        public Map<ZigBeeDeviceStateUUID, State> showLastValues(ZigBeeDeviceEntity zigBeeDeviceEntity, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
            return zigBeeDeviceEntity.showLastValues(zigBeeDeviceEntity, zigBeeDeviceUpdateValueListener);
        }

        @UIMethodAction(name = "ACTION.REDISCOVERY")
        public String rediscoveryNode(ZigBeeDeviceEntity zigBeeDeviceEntity) {
            return zigBeeDeviceEntity.rediscoveryNode();
        }

        @UIMethodAction(name = "ACTION.PERMIT_JOIN")
        public String permitJoin(ZigBeeDeviceEntity zigBeeDeviceEntity, EntityContext entityContext) {
            return zigBeeDeviceEntity.permitJoin(entityContext);
        }

        @UIMethodAction(name = "ACTION.ZIGBEE_PULL_CHANNELS")
        public String pullChannels(ZigBeeDeviceEntity zigBeeDeviceEntity) {
            return zigBeeDeviceEntity.pullChannels();
        }

        public List<Option> selectModelIdentifier() {
            return ZigbeeRequireEndpoints.get().getZigbeeRequireEndpoints().stream().map(c ->
                    Option.of(c.getModelId(), c.getName()).setImageRef(c.getImage())).collect(Collectors.toList());
        }
    }
}

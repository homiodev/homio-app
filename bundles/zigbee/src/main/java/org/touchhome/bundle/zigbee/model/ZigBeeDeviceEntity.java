package org.touchhome.bundle.zigbee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.api.ui.method.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.method.UIMethodAction;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.ZigBeeDevice;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.ZigBeeNodeDescription;
import org.touchhome.bundle.zigbee.requireEndpoint.ZigbeeRequireEndpoint;
import org.touchhome.bundle.zigbee.requireEndpoint.ZigbeeRequireEndpoints;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeDiscoveryDurationSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;
import org.touchhome.bundle.zigbee.workspace.ZigBeeDeviceUpdateValueListener;

import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarMenu(icon = "fas fa-bezier-curve", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, bg = "#de9ed7", order = 5)
public class ZigBeeDeviceEntity extends DeviceBaseEntity<ZigBeeDeviceEntity> {

    public static final String PREFIX = "zb_";

    @JsonIgnore
    private Integer networkAddress;

    @UIField(onlyEdit = true, order = 100)
    @UIFieldNumber(min = 1, max = 86400)
    private Integer reportingTimeMin = 1; // The minimum time period in seconds between device state updates

    @UIField(onlyEdit = true, order = 101)
    @UIFieldNumber(min = 1, max = 86400)
    private Integer reportingTimeMax = 900; // The maximum time period in seconds between device state updates

    @UIField(onlyEdit = true, order = 103)
    @UIFieldNumber(min = 15, max = 86400)
    private Integer poolingPeriod = 900; // The time period in seconds between subsequent polls

    @Transient
    @UIField(readOnly = true, order = 10, type = UIFieldType.String)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @UIFieldColorMatch(value = "ONLINE", color = "#1F8D2D")
    @UIFieldColorMatch(value = "OFFLINE", color = "#B22020")
    @UIFieldColorMatch(value = "UNKNOWN", color = "#818744")
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    @Transient
    @UIField(readOnly = true, order = 100)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.json, autoFormat = true)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private ZigBeeNodeDescription zigBeeNodeDescription;

    @UIField(order = 50)
    @UIFieldTextWithSelection(method = "selectModelIdentifier")
    @UIFieldSelectValueOnEmpty(label = "zigbee.action.selectModelIdentifier", color = "#A7D21E", method = "selectModelIdentifier")
    private String modelIdentifier;

    @Transient
    @JsonIgnore
    private ZigBeeDevice zigBeeDevice;

    @Transient
    @UIField(order = 40, type = UIFieldType.Selection, readOnly = true, color = "#7FBBCC")
    @UIFieldExpand
    @UIFieldCreateWorkspaceVariableOnEmpty
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<Map<Option, String>> availableLinks;

    //TODO: fix this: @UIField(order = 50, type = UIFieldType.Selection, onlyEdit = true)
    private String imageIdentifier;

    ZigBeeDeviceEntity setModelIdentifier(String modelIdentifier) {
        this.modelIdentifier = modelIdentifier;
        tryEvaluateImageIdentifier();

        if (this.getTitle().equals(this.getIeeeAddress())) {
            ZigbeeRequireEndpoint zigbeeRequireEndpoint = ZigbeeRequireEndpoints.get().getZigbeeRequireEndpoint(modelIdentifier);
            if (zigbeeRequireEndpoint != null) {
                String describeName = En.get().findPathText(zigbeeRequireEndpoint.getName());
                if (describeName != null) {
                    setName(describeName + "(" + getNetworkAddress() + ")");
                }
            }
        }
        return this;
    }

    public List<Option> selectModelIdentifier() {
        return ZigbeeRequireEndpoints.get().getZigbeeRequireEndpoints().stream().map(c ->
                Option.of(c.getModelId(), c.getName()).setImageRef(c.getImage())).collect(Collectors.toList());
    }

    @UIMethodAction(name = "ACTION.INITIALIZE_ZIGBEE_NODE")
    public String initializeZigbeeNode() {
        zigBeeDevice.initialiseZigBeeNode();
        return "ACTION.RESPONSE.NODE_INITIALIZATION_STARTED";
    }

    @UIMethodAction(name = "ACTION.SHOW_LAST_VALUES", responseAction = UIMethodAction.ResponseAction.ShowJson)
    public Map<ZigBeeDeviceStateUUID, State> showLastValues(ZigBeeDeviceEntity zigBeeDeviceEntity, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        return zigBeeDeviceUpdateValueListener.getDeviceStates(zigBeeDeviceEntity.getIeeeAddress());
    }

    @UIMethodAction(name = "ACTION.REDISCOVERY")
    public String rediscoveryNode() {
        if (zigBeeDevice == null) {
            throw new IllegalStateException("Unable to find zigbee node with ieeeAddress: " + getIeeeAddress());
        }
        zigBeeDevice.discoveryNodeDescription(this.modelIdentifier);
        return "ACTION.RESPONSE.REDISCOVERY_STARTED";
    }

    @UIMethodAction(name = "ACTION.ZIGBEE_PULL_CHANNELS")
    public String pullChannels() {
        if (zigBeeDevice == null) {
            throw new IllegalStateException("Unable to find zigbee node with ieeeAddress: " + getIeeeAddress());
        }
        new Thread(zigBeeDevice.getPoolingThread()).start();
        return "ACTION.RESPONSE.ZIGBEE_PULL_CHANNELS_STARTED";
    }

    @UIMethodAction(name = "ACTION.PERMIT_JOIN")
    public String permitJoin(EntityContext entityContext) {
        if (entityContext.getSettingValue(ZigbeeStatusSetting.class) != DeviceStatus.ONLINE) {
            throw new IllegalStateException("DEVICE_OFFLINE");
        }
        if (zigBeeDevice == null) {
            throw new IllegalStateException("Unable to find zigbee node with ieeeAddress: " + getIeeeAddress());
        }
        ZigBeeCoordinatorHandler zigBeeCoordinatorHandler = entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
        boolean join = zigBeeCoordinatorHandler.permitJoin(zigBeeDevice.getNodeIeeeAddress(), entityContext.getSettingValue(ZigbeeDiscoveryDurationSetting.class));
        return join ? "ACTION.RESPONSE.STARTED" : "ACTION.RESPONSE.ERROR";
    }

    @Override
    public String toString() {
        return "ZigBee [modelIdentifier='" + modelIdentifier + "]. IeeeAddress-" + getIeeeAddress() + ". Name";
    }

    void setZigBeeNodeDescription(ZigBeeNodeDescription zigBeeNodeDescription) {
        this.zigBeeNodeDescription = zigBeeNodeDescription;
        this.status = zigBeeNodeDescription.getDeviceStatus();
        tryEvaluateModelDescription(zigBeeNodeDescription);
        tryEvaluateImageIdentifier();
    }

    private void tryEvaluateModelDescription(ZigBeeNodeDescription zigBeeNodeDescription) {
        if (zigBeeNodeDescription != null && zigBeeNodeDescription.getChannels() != null && this.modelIdentifier == null) {
            ZigbeeRequireEndpoint property = ZigbeeRequireEndpoints.get().findByNode(zigBeeNodeDescription);
            this.modelIdentifier = property == null ? null : property.getModelId();
        }
    }

    private void tryEvaluateImageIdentifier() {
        if (this.imageIdentifier == null && modelIdentifier != null) {
            this.imageIdentifier = ZigbeeRequireEndpoints.get().getImage(modelIdentifier);
        }
    }
}

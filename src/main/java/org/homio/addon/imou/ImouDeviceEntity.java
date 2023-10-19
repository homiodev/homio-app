package org.homio.addon.imou;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_ICON;
import static org.homio.addon.imou.service.ImouDeviceService.CONFIG_DEVICE_SERVICE;
import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Entity;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO.ImouChannel;
import org.homio.addon.imou.service.ImouDeviceService;
import org.homio.api.EntityContext;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.UISidebarMenu.TopSidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.widget.template.WidgetDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarMenu(icon = IMOU_ICON,
               order = 150,
               bg = IMOU_COLOR,
               parent = TopSidebarMenu.DEVICES,
               allowCreateNewItems = true,
               overridePath = "imou",
               filter = {"*:fas fa-filter:#8DBA73", "status:fas fa-heart-crack:#C452C4"},
               sort = {
                   "name~#FF9800:fas fa-arrow-up-a-z:fas fa-arrow-down-z-a",
                   "updated~#7EAD28:fas fa-clock-rotate-left:fas fa-clock-rotate-left fa-flip-horizontal",
                   "status~#7EAD28:fas fa-turn-up:fas fa-turn-down",
                   "place~#9C27B0:fas fa-location-dot:fas fa-location-dot fa-rotate-180"
               })
public final class ImouDeviceEntity extends DeviceBaseEntity
    implements
    DeviceEndpointsBehaviourContract,
    HasFirmwareVersion,
    EntityService<ImouDeviceService, ImouDeviceEntity>, HasEntityLog {

    @Override
    public @NotNull String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
            getTitle(),
            getIeeeAddress(),
            defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(ImouDeviceService::getEndpoints).orElse(Map.of());
    }

    @UIField(order = 1, hideOnEmpty = true, fullWidth = true, color = "#89AA50", type = HTML, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldColorBgRef(value = "statusColor", animate = true)
    public String getDescription() {
        String message = getStatusMessage();
        if (message != null && message.contains("Failed to connect")) {
            return "IMOU.CONNECT_ISSUE";
        }
        return null;
    }

    @Override
    @UIField(order = 20, required = true, inlineEditWhenEmpty = true, label = "deviceID")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("GENERAL")
    public @Nullable String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @UIField(order = 1, hideOnEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup(value = "DEVICE", order = 8, borderColor = "#7331AD")
    public String getModel() {
        return super.getModel();
    }

    @UIField(order = 2, hideInEdit = true, disableEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public boolean getTlsEnable() {
        return getJsonData("tls", false);
    }

    @UIField(order = 3, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getBrand() {
        return getJsonData("brand");
    }

    //@UIField(order = 4, hideInEdit = true, type = Chips)
    //@UIFieldShowOnCondition("return !context.get('compactMode')")
    //@UIFieldGroup("DEVICE")
    @JsonIgnore
    public List<String> getCapabilities() {
        return getJsonDataList("cap");
    }

    @UIField(order = 5, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getCatalog() {
        return getJsonData("cat");
    }

    @UIField(order = 6, hideOnEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public int getChannel() {
        return getJsonData("ch", -1);
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @Override
    public @NotNull ConfigDeviceDefinitionService getConfigDeviceDefinitionService() {
        return ImouDeviceService.CONFIG_DEVICE_SERVICE;
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return getService().findDevices();
    }

    @Override
    public String getDefaultName() {
        return "Generic Tuya Device";
    }

    @SneakyThrows
    public boolean tryUpdateDeviceEntity(ImouDeviceDTO device) {
        long hashCode = getEntityHashCode();
        setName(device.name);
        setIeeeAddress(device.deviceId);
        setJsonData("cat", device.deviceCatalog);
        setJsonData("brand", device.brand);
        setJsonData("tls", device.tlsEnable);
        setJsonData("model", device.deviceModel);
        Set<String> capabilities = Arrays.stream(device.ability.split(",")).collect(Collectors.toSet());
        capabilities.add("MotionDetect");
        if (capabilities.contains("WLM")) {
            capabilities.add("Linkagewhitelight");
        }
        if (capabilities.contains("WLAN")) {
            capabilities.add("pushNotifications");
        }

        setJsonDataList("cap", capabilities);
        setJsonData("ch", device.channelNum);
        setJsonData("fv", device.version);
        setJsonData("channels", OBJECT_MAPPER.writeValueAsString(device.channels));
        setImageIdentifier(device.deviceModel + ".png");
        return hashCode != getEntityHashCode();
    }

    @Override
    public long getEntityServiceHashCode() {
        return Objects.hashCode(getIeeeAddress()) +
            getJsonDataHashCode("cat", "brand", "tls", "model", "cap", "cat", "ch", "fv", "channels");
    }

    @Override
    public String getFirmwareVersion() {
        return getJsonData("fv");
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "imou-device";
    }

    public boolean isCompactMode() {
        return getEntityContext().setting().getValue(ImouEntityCompactModeSetting.class);
    }

    @JsonIgnore
    @SneakyThrows
    public Map<Integer, ImouChannel> getChannels() {
        String channels = getJsonDataRequire("channels", "");
        if (channels.isEmpty()) {
            return Map.of();
        } else {
            List<ImouChannel> o = OBJECT_MAPPER.readValue(channels, new TypeReference<>() {
            });
            return o.stream().collect(Collectors.toMap(ImouChannel::getChannelId, i -> i));
        }
    }

    @Override
    public void logBuilder(EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID("org.homio");
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        @NotNull List<ConfigDeviceDefinition> configDeviceDefinitions = getService().findDevices();
        List<WidgetDefinition> widgetDefinitions = CONFIG_DEVICE_SERVICE.getDeviceWidgets(configDeviceDefinitions);
        uiInputBuilder.getEntityContext().widget().createTemplateWidgetActions(uiInputBuilder, this, widgetDefinitions);
    }

    @Override
    public @NotNull Class<ImouDeviceService> getEntityServiceItemClass() {
        return ImouDeviceService.class;
    }

    @Override
    public @NotNull ImouDeviceService createService(@NotNull EntityContext entityContext) {
        return new ImouDeviceService(entityContext, this);
    }

    @Override
    public @Nullable String getFallbackImageIdentifier() {
        if (getJsonData().has("icon")) {
            return "https://images.imoucn.com/%s".formatted(getJsonData("icon"));
        }
        return null;
    }
}

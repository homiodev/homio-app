package org.homio.addon.tasmota;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_COLOR;
import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_ICON;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.UISidebarMenu.TopSidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Getter
@Setter
@Entity
@NoArgsConstructor
@UISidebarMenu(icon = TASMOTA_ICON,
               order = 150,
               bg = TASMOTA_COLOR,
               parent = TopSidebarMenu.DEVICES,
               overridePath = "tasmota",
               filter = {"*:fas fa-filter:#8DBA73", "status:fas fa-heart-crack:#C452C4"},
               sort = {
                   "name~#FF9800:fas fa-arrow-up-a-z:fas fa-arrow-down-z-a",
                   "status~#7EAD28:fas fa-turn-up:fas fa-turn-down",
                   "place~#9C27B0:fas fa-location-dot:fas fa-location-dot fa-rotate-180"
               })
public final class ESPHomeDeviceEntity extends DeviceBaseEntity implements
    DeviceEndpointsBehaviourContract,
    HasFirmwareVersion,
    EntityService<org.homio.addon.tasmota.ESPHomeDeviceService, ESPHomeDeviceEntity> {

    public static final String PREFIX = "tasmota";

    public ESPHomeDeviceEntity setFullTopic(String fullTopic) {
        setJsonData("ft", fullTopic);
        return this;
    }

    @Override
    @UIField(order = 10, hideOnEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getName() {
        return optService().map(service ->
            service.getAttributes().path("Status").path("FriendlyName")
                   .path(0).asText()).orElse(null);
    }

    public void setName(String value) {
        TasmotaProjectService.INSTANCE.publish(this, "DeviceName", value);
    }

    @Override
    public @Nullable String getFirmwareVersion() {
        String version = optService().map(service ->
            service.getAttributes().path("StatusFWR").path("Version").asText()).orElse(null);
        if (version != null && version.contains("(")) {
            return version.substring(0, version.indexOf("("));
        }
        return version;
    }

    @UIField(order = 100, hideOnEmpty = true, type = UIFieldType.HTML, hideInEdit = true)
    public String getIpAddress() {
        return optService().map(service -> {
            String ipAddress = service.getAttributes().path("StatusNET").path("IPAddress").asText();
            if (StringUtils.isNotEmpty(ipAddress)) {
                return """
                    <i class="fas fa-globe" style="color: #3A7EC4"></i>
                    <a target="_blank" style="margin-left: 5px" href="http://%s">%s</a>"""
                    .formatted(ipAddress, ipAddress);
            }
            return null;
        }).orElse(null);
    }

    @UIField(order = 110, hideOnEmpty = true, hideInEdit = true)
    public String getBootCount() {
        return optService().map(service ->
            service.getAttributes().path("StatusPRM").path("BootCount").asText()).orElse(null);
    }

    @UIField(order = 120, hideOnEmpty = true, hideInEdit = true)
    public String getUptime() {
        return optService().map(service ->
            service.getAttributes().path("Uptime").asText()).orElse(null);
    }

    @UIField(order = 120, hideOnEmpty = true)
    @UIFieldSlider(min = 10, max = 3600)
    public Integer getTelePeriod() {
        return optService().map(service ->
            service.getAttributes().path("StatusLOG").path("TelePeriod").asInt()).orElse(null);
    }

    public void setTelePeriod(@Min(10) @Max(3600) int value) {
        TasmotaProjectService.INSTANCE.publish(this, "teleperiod", String.valueOf(value));
        TasmotaProjectService.INSTANCE.publish(this, "status", "0");
    }

    public @NotNull String getFullTopic() {
        return getJsonData("ft");
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return Objects.hashCode(getIeeeAddress());
    }

    @Override
    public @NotNull Class<org.homio.addon.tasmota.ESPHomeDeviceService> getEntityServiceItemClass() {
        return org.homio.addon.tasmota.ESPHomeDeviceService.class;
    }

    @Override
    public @NotNull org.homio.addon.tasmota.ESPHomeDeviceService createService(@NotNull Context context) {
        return new org.homio.addon.tasmota.ESPHomeDeviceService(context, this);
    }

    @Override
    public @NotNull String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
            getTitle(),
            getIeeeAddress(),
            defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return optService().map(org.homio.addon.tasmota.ESPHomeDeviceService::findDevices).orElse(List.of());
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(org.homio.addon.tasmota.ESPHomeDeviceService::getEndpoints).orElse(Map.of());
    }

    @UIField(order = 3, disableEdit = true, label = "ieeeAddress")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("GENERAL")
    public String getIeeeAddressLabel() {
        return trimToEmpty(getIeeeAddress()).toUpperCase();
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public @NotNull String getIeeeAddress() {
        return Objects.requireNonNull(super.getIeeeAddress());
    }

    public boolean isCompactMode() {
        return context().setting().getValue(org.homio.addon.tasmota.ESPHomeCompactModeSetting.class);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
    }

    @Override
    public String getDefaultName() {
        return "Tasmota";
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return PREFIX;
    }

    @SneakyThrows
    @JsonIgnore
    @UIContextMenuAction(value = "GET_ATTRIBUTES", icon = "fas fa-eye")
    public ActionResponseModel getAttributes() {
        return ActionResponseModel.showJson("ATTRIBUTES", getService().getAttributes());
    }

    @SneakyThrows
    @JsonIgnore
    @UIContextMenuAction(value = "RESTART",
                         icon = "fas fa-power-off",
                         confirmMessage = "W.CONFIRM.TASMOTA_RESTART",
                         confirmMessageDialogColor = "#4E481E",
                         iconColor = Color.RED)
    public ActionResponseModel restart() {
        TasmotaProjectService.INSTANCE.publish(this, "restart", "1");
        return ActionResponseModel.fired();
    }

    @SneakyThrows
    @JsonIgnore
    @UIContextMenuAction(value = "REFRESH",
                         icon = "fas fa-arrows-rotate",
                         iconColor = Color.GREEN)
    public ActionResponseModel refresh() {
        TasmotaProjectService.INSTANCE.initialQuery(this);
        return ActionResponseModel.fired();
    }
}

package org.homio.addon.tuya;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.tuya.internal.cloud.TuyaOpenAPI.gson;
import static org.homio.addon.tuya.service.TuyaDeviceService.CONFIG_DEVICE_SERVICE;
import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.Entity;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.addon.tuya.internal.local.ProtocolVersion;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.EntityContext;
import org.homio.api.entity.DeviceEndpointsBaseEntity;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
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
@UISidebarMenu(icon = "fac fa-bitfocus",
               order = 150,
               bg = "#D68C38",
               allowCreateNewItems = true,
               overridePath = "tuya",
               filter = {"*:fas fa-filter:#8DBA73", "status:fas fa-heart-crack:#C452C4"},
               sort = {
                   "name~#FF9800:fas fa-arrow-up-a-z:fas fa-arrow-down-z-a",
                   "updated~#7EAD28:fas fa-clock-rotate-left:fas fa-clock-rotate-left fa-flip-horizontal",
                   "status~#7EAD28:fas fa-turn-up:fas fa-turn-down",
                   "place~#9C27B0:fas fa-location-dot:fas fa-location-dot fa-rotate-180"
               })
public final class TuyaDeviceEntity extends DeviceEndpointsBaseEntity
    implements EntityService<TuyaDeviceService, DeviceEndpointsBaseEntity>, HasEntityLog {

    private static final Map<String, Map<String, SchemaDp>> SCHEMAS = readSchemaFromFile();

    @Override
    public String getCompactDescriptionImpl() {
        String ip = getIp();
        return (isEmpty(ip) ? "" : "[" + ip + "] ") + defaultIfEmpty(getDescription(), getName());
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(TuyaDeviceService::getEndpoints).orElse(Map.of());
    }

    @UIField(order = 1, hideOnEmpty = true, fullWidth = true, color = "#89AA50", type = HTML, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldColorBgRef(value = "statusColor", animate = true)
    public String getDescription() {
        String message = getStatusMessage();
        if (message != null && message.contains("Failed to connect")) {
            return "TUYA.CONNECT_ISSUE";
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

    @UIField(order = 22, semiRequired = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("GENERAL")
    public String getLocalKey() {
        return getJsonData("localKey");
    }

    public void setLocalKey(String value) {
        setJsonData("localKey", value);
    }

    @UIField(order = 33, type = UIFieldType.IpAddress, semiRequired = true, inlineEditWhenEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("GENERAL")
    public String getIp() {
        return getJsonData("ip");
    }

    public TuyaDeviceEntity setIp(String value) {
        setJsonData("ip", value);
        return this;
    }

    @UIField(order = 1, isRevert = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup(value = "CONNECTION", order = 5, borderColor = "#3880B0")
    public ProtocolVersion getProtocolVersion() {
        return getJsonDataEnum("pv", ProtocolVersion.V3_4);
    }

    public void setProtocolVersion(ProtocolVersion value) {
        setJsonData("pv", value);
    }

    @UIField(order = 2, isRevert = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("CONNECTION")
    @UIFieldSlider(min = 10, max = 300, header = "s")
    public int getPollingInterval() {
        return getJsonData("pi", 30);
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("CONNECTION")
    @UIFieldSlider(min = 10, max = 300, header = "s")
    public int getReconnectInterval() {
        return getJsonData("ri", 30);
    }

    public void setReconnectInterval(int value) {
        setJsonData("ri", value);
    }

    public void setPollingInterval(int value) {
        setJsonData("pi", value);
    }

    @UIField(order = 1, hideOnEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup(value = "DEVICE", order = 8, borderColor = "#7331AD")
    public String getDeviceModel() {
        return getModel();
    }

    public void setDeviceModel(String value) {
        setJsonData("model", value);
    }

    @UIField(order = 2, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getUuid() {
        return getJsonData("uuid");
    }

    public void setUuid(String value) {
        setJsonData("uuid", value);
    }

    @UIField(order = 3, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getMac() {
        return getJsonData("mac");
    }

    public void setMac(String category) {
        setJsonData("mac", category);
    }

    @UIField(order = 4, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getProductId() {
        return getJsonData("pid");
    }

    public void setProductId(String category) {
        setJsonData("pid", category);
    }

    @UIField(order = 5, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getCategory() {
        return getJsonData("cg");
    }

    public void setCategory(String category) {
        setJsonData("cg", category);
    }

    @UIField(order = 6, hideOnEmpty = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public String getOwnerID() {
        return getJsonData("oid");
    }

    public void setOwnerID(String ownerId) {
        setJsonData("oid", ownerId);
    }

    @UIField(order = 7, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("DEVICE")
    public boolean isSubDevice() {
        return getJsonData("sb", false);
    }

    public void setSubDevice(boolean value) {
        setJsonData("sb", value);
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @Override
    public @Nullable String getModel() {
        return getJsonData("model");
    }

    @Override
    public @NotNull ConfigDeviceDefinitionService getConfigDeviceDefinitionService() {
        return TuyaDeviceService.CONFIG_DEVICE_SERVICE;
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return getService().findDevices();
    }

    @Override
    public String getDefaultName() {
        return "Generic Tuya Device";
    }

    public boolean tryUpdateDeviceEntity(TuyaDeviceDTO device, String deviceMac) {
        int hashCode = getEntityHashCode();
        setCategory(device.category);
        setMac(deviceMac);
        setLocalKey(device.localKey);
        setName(device.name);
        setIeeeAddress(device.id);
        setUuid(device.uuid);
        setDeviceModel(device.model);
        setProductId(device.productId);
        setSubDevice(device.subDevice);
        setOwnerID(device.ownerId);
        setIcon(device.icon);
        if (isNotEmpty(device.productId)) {
            setImageIdentifier(device.productId + ".png");
        }
        return hashCode != getEntityHashCode();
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "tuya-device";
    }

    @SneakyThrows
    public void setSchema(List<SchemaDp> schemaDps) {
        setJsonData("schema", OBJECT_MAPPER.writeValueAsString(schemaDps));
    }

    public boolean isCompactMode() {
        return getEntityContext().setting().getValue(TuyaEntityCompactModeSetting.class);
    }

    @JsonIgnore
    @SneakyThrows
    public @NotNull Map<String, SchemaDp> getSchema() {
        Map<String, SchemaDp> schema = SCHEMAS.get(getProductId());
        if (schema == null) {
            String rawSchema = getJsonDataRequire("schema", "");
            if (rawSchema.isEmpty()) {
                schema = Map.of();
            } else {
                List<SchemaDp> o = OBJECT_MAPPER.readValue(rawSchema, new TypeReference<>() {
                });
                schema = o.stream().collect(Collectors.toMap(SchemaDp::getCode, i -> i));
            }
        }
        return schema;
    }

    public long getDeepHashCode() {
        return Objects.hashCode(getIeeeAddress()) + getJsonDataHashCode("localKey", "pv", "pi", "ri", "cg", "mac", "pid", "ip");
    }

    @UIContextMenuAction(value = "TUYA.FETCH_DEVICE_INFO", icon = "fas fa-barcode", iconColor = Color.PRIMARY_COLOR)
    public ActionResponseModel fetchDeviceInfo(EntityContext entityContext) {
        getService().tryFetchDeviceInfo();
        return ActionResponseModel.success();
    }

    @Override
    public void logBuilder(EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID("org.homio");
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        @NotNull List<ConfigDeviceDefinition> configDeviceDefinitions = getService().findDevices();
        List<WidgetDefinition> widgetDefinitions = CONFIG_DEVICE_SERVICE.getDeviceWidgets(configDeviceDefinitions);
        getEntityContext().widget().createTemplateWidgetActions(uiInputBuilder, this, widgetDefinitions);
    }

    @Override
    public @NotNull Class<TuyaDeviceService> getEntityServiceItemClass() {
        return TuyaDeviceService.class;
    }

    @Override
    public @NotNull TuyaDeviceService createService(@NotNull EntityContext entityContext) {
        return new TuyaDeviceService(entityContext);
    }

    @SneakyThrows
    private static Map<String, Map<String, SchemaDp>> readSchemaFromFile() {
        InputStream resource = TuyaDeviceEntity.class.getClassLoader().getResourceAsStream("tuya-schema.json");
        if (resource == null) {
            throw new IllegalStateException("Unable to find 'tuya-schema.json' file");
        }
        try (InputStreamReader reader = new InputStreamReader(resource)) {
            Type schemaListType = TypeToken.getParameterized(Map.class, String.class, SchemaDp.class).getType();
            Type schemaType = TypeToken.getParameterized(Map.class, String.class, schemaListType).getType();
            return Objects.requireNonNull(gson.fromJson(reader, schemaType));
        }
    }

    @Override
    public @Nullable String getFallbackImageIdentifier() {
        if (getJsonData().has("icon")) {
            return "https://images.tuyacn.com/%s".formatted(getJsonData("icon"));
        }
        return null;
    }
}

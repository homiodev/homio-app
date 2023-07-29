package org.homio.addon.tuya;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.internal.local.ProtocolVersion;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.EntityContext;
import org.homio.api.entity.DeviceBaseEntity;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.homio.addon.tuya.internal.cloud.TuyaOpenAPI.gson;
import static org.homio.addon.tuya.service.TuyaDeviceService.CONFIG_DEVICE_SERVICE;
import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-gamepad", color = "#0088CC")
public final class TuyaDeviceEntity extends MiscEntity<TuyaDeviceEntity>
        implements DeviceBaseEntity.HasEndpointsDevice, EntityService<TuyaDeviceService, TuyaDeviceEntity>, HasEntityLog {

    private static final Map<String, Map<String, SchemaDp>> SCHEMAS = readSchemaFromFile();

    @Override
    public @NotNull Map<String, DeviceEndpoint> getDeviceEndpoints() {
        return getService().getEndpoints()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @UIField(order = 1, hideOnEmpty = true, fullWidth = true, color = "#89AA50", type = HTML)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldColorBgRef(value = "statusColor", animate = true)
    @UIFieldGroup(value = "NAME", order = 1, borderColor = "#CDD649")
    public String getDescription() {
        return "some doc";
    }

    @Override
    @UIField(order = 30, required = true, inlineEditWhenEmpty = true, label = "deviceID")
    public @Nullable String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @UIField(order = 3)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("NAME")
    public @NotNull String getModel() {
        return getJsonDataRequire("model", "tuya_unknown");
    }

    public void setModel(String value) {
        setJsonData("model", value);
    }

    @UIField(order = 4)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("NAME")
    public String getUuid() {
        return getJsonData("uuid");
    }

    public void setUuid(String value) {
        setJsonData("uuid", value);
    }

    @UIField(order = 35, semiRequired = true)
    public String getLocalKey() {
        return getJsonData("localKey");
    }

    public void setLocalKey(String value) {
        setJsonData("localKey", value);
    }

    @UIField(order = 40, type = UIFieldType.IpAddress, semiRequired = true, inlineEditWhenEmpty = true)
    public String getIp() {
        return getJsonData("ip");
    }

    public TuyaDeviceEntity setIp(String value) {
        setJsonData("ip", value);
        return this;
    }

    @UIField(order = 45)
    public ProtocolVersion getProtocolVersion() {
        return getJsonDataEnum("pv", ProtocolVersion.V3_4);
    }

    public void setProtocolVersion(ProtocolVersion value) {
        setJsonData("pv", value);
    }

    @UIField(order = 50, isRevert = true)
    @UIFieldSlider(min = 10, max = 60, header = "s", extraValue = "0")
    public int getPollingInterval() {
        return getJsonData("pi", 0);
    }

    public void setPollingInterval(int value) {
        setJsonData("pi", value);
    }

    @Override
    public String getDefaultName() {
        return "Generic Tuya Device";
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "tuya-device";
    }

    @UIField(order = 100)
    public String getCategory() {
        return getJsonData("cg");
    }

    public void setCategory(String category) {
        setJsonData("cg", category);
    }

    @UIField(order = 110)
    public String getMac() {
        return getJsonData("mac");
    }

    public void setMac(String category) {
        setJsonData("mac", category);
    }

    @UIField(order = 120)
    public String getProductId() {
        return getJsonData("pid");
    }

    public void setProductId(String category) {
        setJsonData("pid", category);
    }

    @UIField(order = 300)
    public boolean isSubDevice() {
        return getJsonData("sb", false);
    }

    public void setSubDevice(boolean value) {
        setJsonData("sb", value);
    }

    @UIField(order = 400)
    public String getIcon() {
        return getJsonData("icon");
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 500, hideOnEmpty = true)
    public String getOwnerID() {
        return getJsonData("oid");
    }

    public void setOwnerID(String ownerId) {
        setJsonData("oid", ownerId);
    }

    @SneakyThrows
    public void setSchema(List<SchemaDp> schemaDps) {
        setJsonData("schema", OBJECT_MAPPER.writeValueAsString(schemaDps));
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
                schema = o.stream().collect(Collectors.toMap(i -> i.code, i -> i));
            }
        }
        return schema;
    }

    public long getDeepHashCode() {
        return Objects.hashCode(getIeeeAddress()) + getJsonDataHashCode("localKey", "pv", "pi", "cg", "mac", "pid");
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
        return new TuyaDeviceService(entityContext, this);
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
}

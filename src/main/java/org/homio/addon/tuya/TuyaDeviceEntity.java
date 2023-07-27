package org.homio.addon.tuya;

import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.internal.local.ProtocolVersion;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.addon.z2m.util.ApplianceModel;
import org.homio.addon.z2m.util.ZigBeeUtil;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTitleRef;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-gamepad", color = "#0088CC")
public final class TuyaDeviceEntity extends MiscEntity<TuyaDeviceEntity>
    implements EntityService<TuyaDeviceService, TuyaDeviceEntity>, HasEntityLog {

    @Override
    @UIField(order = 30, required = true, inlineEditWhenEmpty = true, label = "deviceID")
    public @Nullable String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @UIField(order = 35)
    public String getLocalKey() {
        return getJsonData("localKey");
    }

    public void setLocalKey(String value) {
        setJsonData("localKey", value);
    }

    @UIField(order = 40, type = UIFieldType.IpAddress)
    public String getIp() {
        return getJsonData("ip");
    }

    public void setIp(String value) {
        setJsonData("ip", value);
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

    @Override
    public @NotNull Class<TuyaDeviceService> getEntityServiceItemClass() {
        return TuyaDeviceService.class;
    }

    @Override
    public @NotNull TuyaDeviceService createService(@NotNull EntityContext entityContext) {
        return new TuyaDeviceService(entityContext, this);
    }

    @SneakyThrows
    public void setSchema(List<SchemaDp> schemaDps) {
        setJsonData("schema", OBJECT_MAPPER.writeValueAsString(schemaDps));
    }

    @JsonIgnore
    @SneakyThrows
    public @NotNull List<SchemaDp> getSchemaDps() {
        String schema = getJsonData("schema", "");
        return schema != null && schema.isEmpty() ?
            List.of() : OBJECT_MAPPER.readValue(schema, new TypeReference<>() {});
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

    @UIField(order = 9999)
    @UIFieldInlineEntities(bg = "#27FF0005")
    public List<TuyaPropertyEntity> getEndpointClusters() {
        return getService().getProperties().values().stream()
                            .filter(DeviceEndpoint::isVisible)
                            .map(property -> new TuyaPropertyEntity(property, this))
                            .sorted()
                            .collect(Collectors.toList());
    }

    @Getter
    public static class TuyaPropertyEntity implements Comparable<TuyaPropertyEntity> {

        private String entityID;

        @UIField(order = 2, type = HTML)
        private String title;

        @JsonIgnore
        private TuyaDeviceEntity entity;

        private String valueTitle;

        @JsonIgnore
        private int order;

        public TuyaPropertyEntity(TuyaDeviceProperty property, TuyaDeviceEntity entity) {
            this.entity = entity;
            this.entityID = property.getIeeeAddress();
            this.order = property.getSchemaDp().id;
            String variableID = property.getVariableID();
            if (variableID != null) {
                String varSource = property.getEntityContext().var().buildDataSource(variableID, false);
                this.title =
                    ("<div class=\"inline-2row_d\"><div class=\"clickable history-link\" data-hl=\"%s\" style=\"color:%s;\"><i class=\"mr-1 "
                        + "%s\"></i>%s</div><span>%s</div></div>").formatted(
                        varSource, property.getIcon().getColor(), property.getIcon().getIcon(),
                        property.getName(false), property.getDescription());
            } else {
                this.title =
                    "<div class=\"inline-2row_d\"><div style=\"color:%s;\"><i class=\"mr-1 %s\"></i>%s</div><span>%s</div></div>".formatted(
                        property.getIcon().getColor(), property.getIcon().getIcon(), property.getName(false), property.getDescription());
            }
            this.property = property;
            this.valueTitle = property.getValue().toString();
            if (ApplianceModel.ENUM_TYPE.equals(property.getExpose().getType())) {
                this.valueTitle = "Values: " + String.join(", ", getProperty().getExpose().getValues());
            }
            this.order = deviceService.getConfigService().getPropertyOrder(property.getExpose().getName());
        }

        @UIField(order = 4, style = "margin-left: auto; margin-right: 8px;")
        @UIFieldInlineEntityWidth(30)
        @UIFieldTitleRef("valueTitle")
        public UIInputEntity getValue() {
            return buildAction().buildAll().iterator().next();
        }

        @Override
        public int compareTo(@NotNull TuyaDeviceEntity.TuyaPropertyEntity o) {
            return Integer.compare(this.order, o.order);
        }

        private @NotNull UIInputBuilder buildAction() {
            return ZigBeeUtil.buildZigbeeActions(property, entityID);
        }
    }
}

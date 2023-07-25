package org.homio.addon.tuya;

import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Entity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.internal.local.ProtocolVersion;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-gamepad", color = "#0088CC")
public final class TuyaDeviceEntity extends MiscEntity<TuyaDeviceEntity>
    implements EntityService<TuyaDeviceService, TuyaDeviceEntity>, HasEntityLog {

    public static final String PREFIX = "tuyad_";

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
    public @NotNull String getEntityPrefix() {
        return PREFIX;
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
        return getJsonDataHashCode(getIeeeAddress(), "localKey", "pv", "pi", "cg", "mac", "pid");
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
}

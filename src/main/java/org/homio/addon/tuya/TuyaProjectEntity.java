package org.homio.addon.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import java.time.Duration;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.service.TuyaDiscoveryService;
import org.homio.addon.tuya.service.TuyaProjectService;
import org.homio.api.EntityContext;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.util.Lang;
import org.homio.api.util.SecureString;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-diagram-project", color = "#0088CC", allowCreateItem = false)
public final class TuyaProjectEntity extends MiscEntity<TuyaProjectEntity>
    implements EntityService<TuyaProjectService, TuyaProjectEntity>,
    HasStatusAndMsg<TuyaProjectEntity> {

    @Override
    public TuyaProjectEntity setStatus(@Nullable Status status, @Nullable String msg) {
        boolean reloadItem = !getStatus().equals(status) || (status == Status.ERROR && !Objects.equals(getStatusMessage(), msg));
        super.setStatus(status, msg);
        if (reloadItem) {
            getEntityContext().ui().updateItem(this);
        }
        return this;
    }

    @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        String message = getStatusMessage();
        if (message != null && message.contains(":")) {
            return Lang.getServerMessage("TUYA.DESCRIPTION_" + message.split(":")[0]);
        }
        return Lang.getServerMessage("TUYA.DESCRIPTION");
    }

    @UIField(order = 1, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaUser")
    @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
    public String getUser() {
        return getJsonData("user");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    @UIField(order = 2, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaPassword")
    @UIFieldGroup("AUTH")
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public void setPassword(String value) {
        setJsonDataSecure("pwd", value);
    }

    @UIField(order = 3, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaAccessID")
    @UIFieldGroup("AUTH")
    public String getAccessID() {
        return getJsonData("accessId");
    }

    public void setAccessID(String value) {
        setJsonData("accessId", value);
    }

    @UIField(order = 4, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaAccessSecret")
    @UIFieldGroup("AUTH")
    public SecureString getAccessSecret() {
        return getJsonSecure("accessSecret");
    }

    public void setAccessSecret(String value) {
        setJsonDataSecure("accessSecret", value);
    }

    @UIField(order = 5, required = true, inlineEditWhenEmpty = true, descriptionLabel = "tuyaCountryCode")
    @UIFieldGroup("AUTH")
    public Integer getCountryCode() {
        if (getJsonData().has("cc")) {
            return getJsonData().getInt("cc");
        }
        return null;
    }

    public TuyaProjectEntity setCountryCode(Integer value) {
        setJsonData("cc", value);
        return this;
    }

    @UIField(order = 6, descriptionLabel = "tuyaProjectSchema")
    @UIFieldGroup("AUTH")
    public ProjectSchema getProjectSchema() {
        return getJsonDataEnum("schema", ProjectSchema.smartLife);
    }

    public void setProjectSchema(ProjectSchema value) {
        setJsonData("schema", value);
    }

    @UIField(order = 7, descriptionLabel = "tuyaDataCenter")
    @UIFieldGroup("AUTH")
    public DataCenter getDataCenter() {
        return getJsonDataEnum("dataCenter", DataCenter.CentralEurope);
    }

    public void setDataCenter(DataCenter value) {
        setJsonData("dataCenter", value);
    }

    @Override
    public String getDefaultName() {
        return "Tuya project";
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "tuya-project";
    }

    @Override
    public @NotNull Class<TuyaProjectService> getEntityServiceItemClass() {
        return TuyaProjectService.class;
    }

    @Override
    public @NotNull TuyaProjectService createService(@NotNull EntityContext entityContext) {
        return new TuyaProjectService(entityContext, this);
    }

    public enum ProjectSchema {
        tuyaSmart, smartLife
    }

    public boolean isValid() {
        return !getUser().isEmpty()
            && !getPassword().asString().isEmpty()
            && !getAccessID().isEmpty()
            && !getAccessSecret().asString().isEmpty()
            && getCountryCode() != null
            && getCountryCode() != 0;
    }

    public long getDeepHashCode() {
        return getJsonDataHashCode("user", "pwd", "accessId", "accessSecret", "cc");
    }

    @RequiredArgsConstructor
    public enum DataCenter implements KeyValueEnum {
        China("China", "https://openapi.tuyacn.com"),
        WesternAmerica("Western America", "https://openapi.tuyaus.com"),
        EasternAmerica("Eastern America (Azure/MS)", "https://openapi-ueaz.tuyaus.com"),
        CentralEurope("Central Europe", "https://openapi.tuyaeu.com"),
        WesternEurope("Western Europe (Azure/MS)", "https://openapi-weaz.tuyaeu.com"),
        India("China", "https://openapi.tuyain.com");

        private final String title;
        @Getter
        private final String url;

        @Override
        public String getValue() {
            return title;
        }
    }

    @UIContextMenuAction(value = "TUYA.SCAN_DEVICES", icon = "fas fa-barcode", iconColor = Color.PRIMARY_COLOR)
    public ActionResponseModel scanDevices(EntityContext entityContext) {
        entityContext.bgp().runWithProgress("tuya-scan-devices").execute(progressBar -> {
            entityContext.getBean(TuyaDiscoveryService.class)
                         .scan(entityContext, progressBar, null);
        });
        return ActionResponseModel.fired();
    }
}

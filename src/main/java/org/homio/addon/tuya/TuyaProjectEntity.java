package org.homio.addon.tuya;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.service.TuyaProjectService;
import org.homio.api.EntityContext;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.util.Lang;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-diagram-project", color = "#0088CC", allowCreateItem = false)
public final class TuyaProjectEntity extends MiscEntity<TuyaProjectEntity>
    implements EntityService<TuyaProjectService, TuyaProjectEntity>,
    HasStatusAndMsg<TuyaProjectEntity> {

    public static final String PREFIX = "tuyap_";
    public static final String DEFAULT_ENTITY_ID = PREFIX + "primary";

    @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        if (!isValid()) {
            return Lang.getServerMessage("tuyaprj.description");
        }
        return null;
    }

    @UIField(order = 1, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
    public String getUser() {
        return getJsonData("user");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    @UIField(order = 2, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup("AUTH")
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public void setPassword(String value) {
        setJsonData("pwd", value);
    }

    @UIField(order = 3, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup("AUTH")
    public String getAccessId() {
        return getJsonData("accessId");
    }

    public void setAccessId(String value) {
        setJsonData("accessId", value);
    }

    @UIField(order = 4, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup("AUTH")
    public SecureString getAccessSecret() {
        return getJsonSecure("accessSecret");
    }

    public void setAccessSecret(String value) {
        setJsonData("accessSecret", value);
    }

    @UIField(order = 5, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup("AUTH")
    public Integer getCountryCode() {
        if (getJsonData().has("cc")) {
            getJsonData().getInt("cc");
        }
        return null;
    }

    public void setCountryCode(Integer value) {
        setJsonData("cc", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("AUTH")
    public ProjectSchema getProjectSchema() {
        return getJsonDataEnum("schema", ProjectSchema.smartLife);
    }

    public void setProjectSchema(ProjectSchema value) {
        setJsonData("schema", value);
    }

    @UIField(order = 7)
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
    public @NotNull String getEntityPrefix() {
        return PREFIX;
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
            && !getAccessId().isEmpty()
            && !getAccessSecret().asString().isEmpty()
            && getCountryCode() != null
            && getCountryCode() != 0;
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
}

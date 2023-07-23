package org.homio.addon.tuya;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import jakarta.persistence.Entity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.EntityContext;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldChipsOptions;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.util.Lang;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-diagram-project", color = "#0088CC")
public final class TuyaProjectEntity extends MiscEntity<TuyaProjectEntity>
    implements EntityService<TuyaProjectService, TuyaProjectEntity>,
    HasStatusAndMsg<TuyaProjectEntity> {

    public static final String PREFIX = "tuyap_";

    @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        if (isEmpty(getUser()) || isEmpty(getPassword()) || isEmpty(getAccessSecret()) || isEmpty(getAccessSecret()) || isEmpty(getCountryCode())) {
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
    public String getAccessSecret() {
        return getJsonData("accessSecret");
    }

    public void setAccessSecret(String value) {
        setJsonData("accessSecret", value);
    }

    @UIField(order = 5, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup("AUTH")
    public String getCountryCode() {
        return getJsonData("cc");
    }

    public void setCountryCode(String value) {
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

    @UIField(order = 7, type = UIFieldType.Chips)
    @UIFieldChipsOptions(values = {
        "https://openapi.tuyacn.com:China",
        "https://openapi.tuyaus.com:Western America",
        "https://openapi-ueaz.tuyaus.com:Eastern America (Azure/MS)",
        "https://openapi.tuyaeu.com:Central Europe",
        "https://openapi-weaz.tuyaeu.com:Western Europe (Azure/MS)",
        "https://openapi.tuyain.com:India",
    })
    @UIFieldGroup("AUTH")
    public List<String> getDataCenter() {
        return getJsonDataList("dataCenter");
    }

    public void setDataCenter(String value) {
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
}

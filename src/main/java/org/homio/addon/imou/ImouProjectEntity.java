package org.homio.addon.imou;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.imou.service.ImouDiscoveryService;
import org.homio.addon.imou.service.ImouProjectService;
import org.homio.api.EntityContext;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldLinkToEntity;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.util.Lang;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarChildren(icon = "fas fa-diagram-project", color = "#0088CC", allowCreateItem = false)
public final class ImouProjectEntity extends MicroControllerBaseEntity
    implements EntityService<ImouProjectService, ImouProjectEntity>,
    HasStatusAndMsg, HasEntityLog {

    @UIField(order = 9999, disableEdit = true, hideInEdit = true)
    @UIFieldInlineEntities(bg = "#27FF000D")
    public List<ImouDeviceInlineEntity> getCoordinatorDevices() {
        return getEntityContext().findAll(ImouDeviceEntity.class)
                                 .stream()
                                 .sorted()
                                 .map(ImouDeviceInlineEntity::new)
                                 .collect(Collectors.toList());
    }

    @Override
    public void setStatus(@Nullable Status status, @Nullable String msg) {
        boolean reloadItem = !getStatus().equals(status) || (status == Status.ERROR && !Objects.equals(getStatusMessage(), msg));
        super.setStatus(status, msg);
        if (reloadItem) {
            getEntityContext().ui().updateItem(this);
        }
    }

    @UIField(order = 1, hideInEdit = true, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        String message = getStatusMessage();
        if (message != null && message.contains(":")) {
            return Lang.getServerMessage("IMOU.DESCRIPTION_" + message.split(":")[0]);
        }
        return Lang.getServerMessage("IMOU.DESCRIPTION");
    }

    @UIField(order = 1, required = true, inlineEditWhenEmpty = true, descriptionLabel = "imouAppUID")
    @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
    public String getAppUID() {
        return getJsonData("appUID");
    }

    public void setAppUID(String value) {
        setJsonData("appUID", value);
    }

    @UIField(order = 5, required = true, inlineEditWhenEmpty = true, descriptionLabel = "imouAppSecret")
    @UIFieldGroup("AUTH")
    public SecureString getAppSecret() {
        return getJsonSecure("appSecret");
    }

    public void setAppSecret(String value) {
        setJsonDataSecure("appSecret", value);
    }

    @UIField(order = 8, descriptionLabel = "imouDataCenter")
    @UIFieldGroup("AUTH")
    public DataCenter getDataCenter() {
        return getJsonDataEnum("dataCenter", DataCenter.CentralEurope);
    }

    public void setDataCenter(DataCenter value) {
        setJsonData("dataCenter", value);
    }

    @Override
    public String getDefaultName() {
        return "Imou project";
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "imou-project";
    }

    @Override
    public @NotNull Class<ImouProjectService> getEntityServiceItemClass() {
        return ImouProjectService.class;
    }

    @Override
    public @NotNull ImouProjectService createService(@NotNull EntityContext entityContext) {
        return new ImouProjectService(entityContext, this);
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder entityLogBuilder) {
        entityLogBuilder.addTopicFilterByEntityID("org.homio");
    }

    public boolean isValid() {
        return !getAppUID().isEmpty() && !getAppSecret().asString().isEmpty();
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("appUID", "appSecret");
    }

    @UIContextMenuAction(value = "IMOU.SCAN_DEVICES", icon = "fas fa-barcode", iconColor = Color.PRIMARY_COLOR)
    public ActionResponseModel scanDevices(EntityContext entityContext) {
        entityContext.bgp().runWithProgress("imou-scan-devices").execute(progressBar -> {
            entityContext.getBean(ImouDiscoveryService.class)
                    .scan(entityContext, progressBar, null);
        });
        return ActionResponseModel.fired();
    }

    @UIContextMenuAction(value = "IMOU.GET_DEVICE_LIST", icon = "fas fa-tape")
    public ActionResponseModel getDevicesList(EntityContext entityContext) {
        return ActionResponseModel.showJson("Imou device list",
            entityContext.getBean(ImouDiscoveryService.class).getDeviceList(entityContext));
    }

    /*@UIContextMenuAction(value = "IMOU.GET_USER_INFO", icon = "fas fa-tape")
    public ActionResponseModel getUserInfo(EntityContext entityContext) {
        return ActionResponseModel.showJson("Tuya device list",
            entityContext.getBean(TuyaOpenAPI.class).getUserInfo());
    }*/

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public @Nullable String getPlace() {
        return null;
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Getter
    @NoArgsConstructor
    private static class ImouDeviceInlineEntity {

        @UIField(order = 1)
        @UIFieldInlineEntityWidth(35)
        @UIFieldLinkToEntity(ImouDeviceEntity.class)
        private String ieeeAddress;

        @UIField(order = 2)
        @UIFieldColorRef("color")
        private String name;

        @UIField(order = 4)
        @UIFieldInlineEntityWidth(10)
        private int endpointsCount;

        private String color;

        public ImouDeviceInlineEntity(ImouDeviceEntity entity) {
            color = entity.getStatus().getColor();
            name = entity.getName();
            if (StringUtils.isEmpty(name) || name.equalsIgnoreCase(entity.getIeeeAddress())) {
                name = entity.getDescription();
            }
            ieeeAddress = entity.getEntityID() + "~~~" + entity.getIeeeAddress();
            endpointsCount = entity.getService().getEndpoints().size();
        }
    }

    @RequiredArgsConstructor
    public enum DataCenter implements KeyValueEnum {
        EasAsia("East Asia Data Center", "https://openapi-sg.easy4ip.com:443/openapi/"),
        CentralEurope("Central Europe Data Center", "https://openapi-fk.easy4ip.com:443/openapi/"),
        WesternAmerica("Western America Data Center", "https://openapi-or.easy4ip.com:443/openapi/");

        private final String title;
        @Getter
        private final String url;

        @Override
        public String getValue() {
            return title;
        }
    }
}

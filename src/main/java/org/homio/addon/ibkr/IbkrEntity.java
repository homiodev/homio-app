package org.homio.addon.ibkr;

import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.util.Lang;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Entity
@UISidebarChildren(icon = "fas fa-square-rss", color = "#308BB3", allowCreateItem = false)
public class IbkrEntity extends MiscEntity implements EntityService<IbkrService>,
        HasStatusAndMsg {

    public static @NotNull IbkrEntity ensureEntity(Context context) {
        IbkrEntity entity = context.db().getEntity(IbkrEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new IbkrEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            entity = context.db().save(entity);
        }
        return entity;
    }

    @UIField(order = 1, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", true);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }

    @UIField(order = 25)
    @UIFieldGroup("GENERAL")
    public int getPort() {
        return getJsonData("port", 5000);
    }

    public void setPort(int value) {
        setJsonData("port", value);
    }

    @Override
    public String getDescriptionImpl() {
        AtomicReference<String> value = new AtomicReference<>("");
        optService().ifPresent(service -> {
            String proxyHost = service.getProxyHost();
            proxyHost = proxyHost.substring("$DEVICE_URL/".length());
            proxyHost  = "http://192.168.1.63:9111/" + proxyHost;
            value.set(Lang.getServerMessage("IBKR_DESCRIPTION", proxyHost));
        });
        return value.get();
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ibkr";
    }

    @Override
    public @Nullable String getDefaultName() {
        return "IBKR";
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("port");
    }

    @Override
    public @NotNull Class<IbkrService> getEntityServiceItemClass() {
        return IbkrService.class;
    }

    @Override
    public @Nullable IbkrService createService(@NotNull Context context) {
        return new IbkrService(context, this);
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @SneakyThrows
    @UIContextMenuAction(value = "RESTART",
            confirmMessage = "W.CONFIRM.RESTART_IBKR",
            confirmMessageDialogColor = UI.Color.ERROR_DIALOG,
            icon = "fas fa-power-off",
            iconColor = "#91293E")
    public ActionResponseModel restart() {
        getOrCreateService(context()).ifPresent(ServiceInstance::restartService);
        return ActionResponseModel.success();
    }

    public String getUrl(String path) {
        return "http://localhost:" + getPort() + "/v1/api/" + path;
    }
}

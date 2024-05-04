package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.Context;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.util.NotificationLevel;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

@Entity
@UISidebarChildren(icon = "fas fa-person-walking-luggage", color = "#B5B812")
public final class UserGuestEntity extends UserBaseEntity {

    @Override
    public @NotNull UserType getUserType() {
        return UserType.GUEST;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "user-guest";
    }

    @Override
    public String getDefaultName() {
        return "Guest user";
    }

    @UIContextMenuAction(value = "GET_ACCESS_URL", icon = "fas fa-globe", iconColor = "#4AA734")
    public ActionResponseModel getAccessURL(Context context) {
        String url = UserGuestEntity.getAccessURL(this);
        context.ui().toastr().sendMessage("Access URL", url, NotificationLevel.success, 60);
        return null;
    }


    public static String getAccessURL(UserGuestEntity entity) {
        String remoteHost = ContextImpl.REQUEST.get().getHeader("origin");
        String encodedText = Base64.getEncoder().encodeToString((MACHINE_IP_ADDRESS + "~~~" + entity.getName()).getBytes());
        return remoteHost + "?aid=" + encodedText;
    }
}

package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.ui.UISidebarChildren;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-person-walking-luggage", color = "#B5B812")
public final class UserGuestEntity extends UserBaseEntity<UserGuestEntity> {

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
}

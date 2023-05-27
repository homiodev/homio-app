package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.ui.UISidebarChildren;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-person-walking-luggage", color = "#B5B812")
public final class UserGuestEntity extends UserBaseEntity<UserGuestEntity> {

    public static final String PREFIX = "ug_";

    @Override
    public @NotNull UserType getUserType() {
        return UserType.GUEST;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return "Guest user";
    }
}

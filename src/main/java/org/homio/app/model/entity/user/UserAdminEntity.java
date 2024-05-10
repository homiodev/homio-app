package org.homio.app.model.entity.user;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Set;

import org.homio.api.ui.UISidebarChildren;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-chalkboard-user", color = "#B5094E", allowCreateItem = false)
public final class UserAdminEntity extends UserBaseEntity {

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "user-admin";
    }

    @Override
    public @NotNull UserType getUserType() {
        return UserType.ADMIN;
    }

    @Override
    public String getDefaultName() {
        return "Admin user";
    }

    public static void ensureUserExists(ContextImpl context) {
        List<UserAdminEntity> users = context.db().findAll(UserAdminEntity.class);
        if (users.isEmpty()) {
            UserAdminEntity entity = new UserAdminEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            context.db().save(entity);
        }
    }
}

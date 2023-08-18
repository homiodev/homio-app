package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.ui.UISidebarChildren;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

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

    public static void ensureUserExists(EntityContextImpl entityContext) {
        List<UserAdminEntity> users = entityContext.findAll(UserAdminEntity.class);
        if (users.isEmpty()) {
            UserAdminEntity entity = new UserAdminEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entityContext.save(entity);
        }
    }
}

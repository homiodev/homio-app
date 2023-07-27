package org.homio.app.model.entity.user;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import jakarta.persistence.Entity;
import java.util.List;
import org.homio.api.ui.UISidebarChildren;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-chalkboard-user", color = "#B5094E", allowCreateItem = false)
public final class UserAdminEntity extends UserBaseEntity<UserAdminEntity> {

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
            entityContext.save(new UserAdminEntity().setEntityID(PRIMARY_DEVICE));
        }
    }
}

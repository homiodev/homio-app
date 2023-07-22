package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import java.util.List;
import org.homio.api.ui.UISidebarChildren;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-chalkboard-user", color = "#B5094E", allowCreateItem = false)
public final class UserAdminEntity extends UserBaseEntity<UserAdminEntity> {

    public static final String PREFIX = "ua_";
    public static final String ENTITY_ID = PREFIX + "primary";

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    public @NotNull UserType getUserType() {
        return UserType.ADMIN;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return "Admin user";
    }

    public static void ensureUserExists(EntityContextImpl entityContext) {
        List<UserAdminEntity> users = entityContext.findAll(UserAdminEntity.class);
        if (users.isEmpty()) {
            entityContext.save(new UserAdminEntity().setEntityID(ENTITY_ID));
        }
    }
}

package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.ui.UISidebarChildren;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
@CreateSingleEntity
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
    public void assertDeleteAccess(BaseEntity entity) {
    }

    @Override
    public void assertEditAccess(BaseEntity entity) {

    }

    @Override
    public void assertViewAccess(BaseEntity entity) {

    }

    @Override
    public String getDefaultName() {
        return "Admin user";
    }
}

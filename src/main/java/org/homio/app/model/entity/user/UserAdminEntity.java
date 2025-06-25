package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.ui.route.UIRouteIdentity;
import org.jetbrains.annotations.NotNull;

@Entity
@CreateSingleEntity
@UIRouteIdentity(icon = "fas fa-chalkboard-user", color = "#B5094E", allowCreateItem = false)
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
  public void assertSettingsAccess(SettingPlugin<?> setting, Context context) {
  }

  @Override
  public String getDefaultName() {
    return "Admin user";
  }
}

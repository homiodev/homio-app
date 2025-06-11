package org.homio.app.model.entity.user;

import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasPermissions;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.route.UIRouteIdentity;
import org.homio.api.util.NotificationLevel;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.Set;
import java.util.function.Predicate;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;
import static org.springframework.http.HttpHeaders.ORIGIN;

@Entity
@UIRouteIdentity(icon = "fas fa-person-walking-luggage", color = "#B5B812")
public final class UserGuestEntity extends UserBaseEntity {

  @SneakyThrows
  public static void assertAction(
      Context context, Predicate<UserGuestEntity> predicate, String message) {
    if (isDisabled(context, predicate)) {
      throw new IllegalAccessException("User is not allowed to access " + message);
    }
  }

  public static boolean isDisabled(Context context, Predicate<UserGuestEntity> predicate) {
    UserEntity user = context.user().getLoggedInUser();
    if (user != null && !user.isAdmin()) {
      UserGuestEntity guest = (UserGuestEntity) user;
      return !predicate.test(guest);
    }
    return false;
  }

  public static void assertLogAccess(Context context) {
    UserGuestEntity.assertAction(context, UserGuestEntity::getLogAccess, "logs");
  }

  public static void assertSshAccess(Context context) {
    UserGuestEntity.assertAction(context, UserGuestEntity::getSshAccess, "ssh");
  }

  public static void assertFileManagerReadAccess(Context context) {
    UserGuestEntity.assertAction(
        context, UserGuestEntity::getFileMangerReadAccess, "read FileManager");
  }

  public static void assertFileManagerWriteAccess(Context context) {
    UserGuestEntity.assertAction(
        context, UserGuestEntity::getFileMangerWriteAccess, "write FileManager");
  }

  @SneakyThrows
  public static void assertConsoleAccess(Context context, ConsolePlugin<?> consolePlugin) {
    if (!consolePlugin.isEnabled()) {
      throw new IllegalAccessException("Console plugin is not enabled");
    }
  }

  public static String getAccessURL(UserGuestEntity entity) {
    String remoteHost = ContextImpl.REQUEST.get().get(ORIGIN);
    String encodedText =
        Base64.getEncoder()
            .encodeToString((MACHINE_IP_ADDRESS + "~~~" + entity.getName()).getBytes());
    return remoteHost + "?aid=" + encodedText;
  }

  public static boolean isEnabledLogAccess(Context context) {
    UserEntity user = context.user().getLoggedInUser();
    if (user != null && !user.isAdmin()) {
      try {
        UserGuestEntity.assertLogAccess(context);
      } catch (Exception ignore) {
        return false;
      }
    }
    return true;
  }

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

  @UIField(order = 1)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 50, value = "ACCESS_WIDGET", borderColor = "#5BB4A5")
  public boolean getUpdateWidget() {
    return getJsonData("perm_upd_wt", false);
  }

  public void setUpdateWidget(boolean value) {
    setJsonData("perm_upd_wt", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_WIDGET")
  public boolean getDeleteWidget() {
    return getJsonData("perm_del_wt", false);
  }

  public void setDeleteWidget(boolean value) {
    setJsonData("perm_del_wt", value);
  }

  @UIField(order = 1)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 55, value = "ACCESS_WIDGET_TAB", borderColor = "#5BB47C")
  public boolean getUpdateWidgetTab() {
    return getJsonData("perm_upd_wtt", false);
  }

  public void setUpdateWidgetTab(boolean value) {
    setJsonData("perm_upd_wtt", value);
  }

  @UIField(order = 2, hideOnEmpty = true)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_WIDGET_TAB")
  public Set<String> getVisibleWidgetTab() {
    return getJsonDataSet("perm_view_wtt");
  }

  public void setVisibleWidgetTab(String value) {
    setJsonData("perm_view_wtt", value);
  }

  @UIField(order = 3, hideOnEmpty = true)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_WIDGET_TAB")
  public Set<String> getFullAccessToWidgetTab() {
    return getJsonDataSet("perm_fa_wtt");
  }

  public void setFullAccessToWidgetTab(String value) {
    setJsonData("perm_fa_wtt", value);
  }

  @UIField(order = 4)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_WIDGET_TAB")
  public boolean getDeleteWidgetTab() {
    return getJsonData("perm_del_wtt", false);
  }

  public void setDeleteWidgetTab(boolean value) {
    setJsonData("perm_del_wtt", value);
  }

  @UIField(order = 1)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 60, value = "ACCESS_WORKSPACE_TAB", borderColor = "#B4AB5B")
  public boolean getUpdateWorkspaceTab() {
    return getJsonData("perm_upd_wst", false);
  }

  public void setUpdateWorkspaceTab(boolean value) {
    setJsonData("perm_upd_wst", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_WORKSPACE_TAB")
  public boolean getDeleteWorkspaceTab() {
    return getJsonData("perm_del_wst", false);
  }

  public void setDeleteWorkspaceTab(boolean value) {
    setJsonData("perm_del_wst", value);
  }

  @UIField(order = 1)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 65, value = "ACCESS_FM", borderColor = "#7D4BB0")
  public boolean getFileMangerReadAccess() {
    return getJsonData("perm_fm_r", false);
  }

  public void setFileMangerReadAccess(boolean value) {
    setJsonData("perm_fm_r", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_FM")
  public boolean getFileMangerWriteAccess() {
    return getJsonData("perm_fm_w", false);
  }

  public void setFileMangerWriteAccess(String value) {
    setJsonData("perm_fm_w", value);
  }

  @UIField(order = 1)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 70, value = "ACCESS_SETTINGS", borderColor = "#B04B9D")
  public boolean getUpdateSettings() {
    return getJsonData("perm_upd_stt", true);
  }

  public void setUpdateSettings(boolean value) {
    setJsonData("perm_del_wst", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_SETTINGS")
  public boolean getRestartDevice() {
    return getJsonData("perm_rst_dev", false);
  }

  public void setRestartDevice(boolean value) {
    setJsonData("perm_rst_dev", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup(order = 75, value = "ACCESS_OTHER", borderColor = "#CA2C2C")
  public boolean getLogAccess() {
    return getJsonData("perm_log", false);
  }

  public void setLogAccess(boolean value) {
    setJsonData("perm_log", value);
  }

  @UIField(order = 2)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_OTHER")
  public boolean getSshAccess() {
    return getJsonData("perm_ssh", false);
  }

  public void setSshAccess(boolean value) {
    setJsonData("perm_ssh", value);
  }

  @UIField(order = 3)
  @UIFieldTab("PERMISSIONS")
  @UIFieldGroup("ACCESS_OTHER")
  public boolean getDisableFullEditAccess() {
    return getJsonData("perm_fae", true);
  }

  public void setDisableFullEditAccess(boolean value) {
    setJsonData("perm_fae", value);
  }

  @Override
  @SneakyThrows
  public void assertDeleteAccess(BaseEntity entity) {
    assertModifyAccess(entity, "delete");

    if (entity instanceof WorkspaceEntity && !getDeleteWorkspaceTab()) {
      throw new IllegalAccessException("User is not allowed to delete workspace tab");
    } else if (entity instanceof WidgetTabEntity tab && !getDeleteWidgetTab()) {
      if (getFullAccessToWidgetTab().contains(tab.getName())) {
        return;
      }
      if (!getDeleteWidgetTab() || !getUpdateWidgetTab()) {
        throw new IllegalAccessException("User is not allowed to delete widget tab");
      }
    } else if (entity instanceof WidgetEntity<?> widget) {
      if (getFullAccessToWidgetTab().contains(widget.getWidgetTabEntity().getName())) {
        return;
      }
      if (!getDeleteWidget() || !getUpdateWidget() || !getUpdateWidgetTab()) {
        throw new IllegalAccessException("User is not allowed to delete widget");
      }
    }
  }

  @Override
  @SneakyThrows
  public void assertEditAccess(BaseEntity entity) {
    assertViewAccess(entity);
    assertModifyAccess(entity, "update");
  }

  private void assertModifyAccess(BaseEntity entity, String action) throws IllegalAccessException {
    if (entity instanceof WorkspaceEntity && !getUpdateWorkspaceTab()) {
      throw new IllegalAccessException("User is not allowed to " + action + " workspace tab");
    } else if (entity instanceof WidgetTabEntity tab) {
      if (getFullAccessToWidgetTab().contains(tab.getName())) {
        return;
      }
      if (!getUpdateWidgetTab()) {
        throw new IllegalAccessException("User is not allowed to " + action + " widget tab");
      }
    } else if (entity instanceof WidgetEntity<?> widget) {
      if (getFullAccessToWidgetTab().contains(widget.getWidgetTabEntity().getName())) {
        return;
      }
      if (!getUpdateWidget() || !getUpdateWidgetTab()) {
        throw new IllegalAccessException("User is not allowed to " + action + " widget");
      }
    }
    if (getDisableFullEditAccess()) {
      throw new IllegalAccessException(
          "User is not allowed to " + action + " " + entity.getTitle());
    }
  }

  @Override
  @SneakyThrows
  public void assertViewAccess(BaseEntity entity) {
    if (entity instanceof WidgetTabEntity tab) {
      Set<String> visibleWidgetTabs = getVisibleWidgetTab();
      if (!visibleWidgetTabs.isEmpty() && !visibleWidgetTabs.contains(tab.getName())) {
        throw new IllegalAccessException("User is not allowed to view widget tab");
      }
    }
    if (entity instanceof HasPermissions permissions) {
      Set<String> hideForUsers = permissions.getHideForUsers();
      if (!hideForUsers.isEmpty() && hideForUsers.contains(getEmail())) {
        throw new IllegalAccessException("User is not allowed to view entity");
      }
    }
  }

  @Override
  public void assertSettingsAccess(SettingPlugin<?> setting, Context context) {
    UserGuestEntity.assertAction(context, UserGuestEntity::getUpdateSettings, "settings");
  }
}

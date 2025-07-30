package org.homio.app.setting.system;

import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SystemLogRequestsSetting
  implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public int order() {
    return 1100;
  }

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull String getSubGroupKey() {
    return "DEBUG";
  }

  @Override
  @SneakyThrows
  public void assertUserAccess(@NotNull Context context, @Nullable UserEntity user) {
    UserEntity.assertAdminAccess(user, "change 'Log Request' action");
  }
}

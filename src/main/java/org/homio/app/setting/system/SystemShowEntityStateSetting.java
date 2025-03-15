package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Show BaseEntity CRUD
 */
public class SystemShowEntityStateSetting
  implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull String getSubGroupKey() {
    return "EVENTS";
  }

  @Override
  public int order() {
    return 500;
  }
}

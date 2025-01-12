package org.homio.app.setting.dashboard;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class DashboardShowActionSetting
  implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.dashboard;
  }

  @Override
  public boolean defaultValue() {
    return true;
  }

  @Override
  public int order() {
    return 300;
  }
}

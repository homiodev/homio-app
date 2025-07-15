package org.homio.app.setting.workspace;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class WorkspaceToolboxAtRightSetting
  implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.workspace;
  }

  @Override
  public int order() {
    return 1200;
  }
}

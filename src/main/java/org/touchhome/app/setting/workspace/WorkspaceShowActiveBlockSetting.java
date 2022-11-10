package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;

public class WorkspaceShowActiveBlockSetting implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public GroupKey getGroupKey() {
    return GroupKey.workspace;
  }

  @Override
  public boolean defaultValue() {
    return false;
  }

  @Override
  public int order() {
    return 900;
  }
}

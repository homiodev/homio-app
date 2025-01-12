package org.homio.app.setting.sidebar;

import org.homio.api.setting.SettingPluginBoolean;

public class SidebarRememberMenuStatusSetting implements SettingPluginBoolean {

  @Override
  public int order() {
    return 0;
  }
}

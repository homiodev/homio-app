package org.touchhome.app.setting.console.ssh;


import org.touchhome.bundle.api.setting.SettingPluginBoolean;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleSshBlinkCursorSetting implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public int order() {
    return 500;
  }

  @Override
  public String[] pages() {
    return new String[]{"ssh"};
  }
}

package org.homio.app.setting.console;

import org.homio.bundle.api.setting.SettingPluginButton;
import org.homio.bundle.api.setting.console.ConsoleSettingPlugin;
import org.homio.bundle.api.ui.UI.Color;
import org.json.JSONObject;

public class ConsoleFMClearCacheButtonSetting implements ConsoleSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public String getIcon() {
    return "fas fa-brush";
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public String getConfirmMsg() {
    return "W.CONFIRM.FM_CLEAR";
  }

  @Override
  public String[] pages() {
    return new String[]{"fm"};
  }
}

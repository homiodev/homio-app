package org.homio.addon.firmata.arduino.setting.header;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.json.JSONObject;

public class ConsoleHeaderGetBoardInfoSetting implements ConsoleHeaderSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-info", "#4279AE");
  }

  @Override
  public String getConfirmMsg() {
    return null;
  }
}

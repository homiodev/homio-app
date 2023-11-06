package org.homio.addon.firmata.arduino.setting.header;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.json.JSONObject;

public class ConsoleHeaderArduinoDeploySketchSetting implements ConsoleHeaderSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public String getConfirmMsg() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-upload", "#2F8B44");
  }

  @Override
  public String[] fireActionsBeforeChange() {
    return new String[]{"st_ShowInlineReadOnlyConsoleHeaderSetting/true", "SAVE"};
  }
}

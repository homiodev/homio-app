package org.homio.addon.firmata.arduino.setting;

import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class ConsoleArduinoUploadUsingProgrammerSetting implements SettingPluginButton, ConsoleSettingPlugin<JSONObject> {

  @Override
  public @Nullable String getConfirmMsg() {
    return "W.CONFIRM.UPLOAD_USING_PROGRAMMER";
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-upload");
  }

  @Override
  public int order() {
    return 300;
  }

  @Override
  public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
    return consolePlugin instanceof ArduinoConsolePlugin;
  }
}

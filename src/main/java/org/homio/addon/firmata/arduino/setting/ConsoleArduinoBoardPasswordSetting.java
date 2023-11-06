package org.homio.addon.firmata.arduino.setting;

import java.util.Collections;
import java.util.List;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class ConsoleArduinoBoardPasswordSetting implements SettingPluginButton, ConsoleSettingPlugin<JSONObject> {

  @Override
  public @Nullable String getConfirmMsg() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-unlock-alt");
  }

  @Override
  public int order() {
    return 200;
  }

  @Override
  public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
    return consolePlugin instanceof ArduinoConsolePlugin;
  }

  @Override
  public List<ActionInputParameter> getInputParameters(Context context, String value) {
    return Collections.singletonList(new ActionInputParameter("PASSWORD", UIActionInput.Type.password, null, null)
        .setDescription("arduino.password_description"));
  }
}

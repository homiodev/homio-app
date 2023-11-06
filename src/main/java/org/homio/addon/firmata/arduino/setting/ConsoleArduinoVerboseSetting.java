package org.homio.addon.firmata.arduino.setting;

import org.homio.api.console.ConsolePlugin;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;

public class ConsoleArduinoVerboseSetting implements SettingPluginBoolean, ConsoleSettingPlugin<Boolean> {

  @Override
  public int order() {
    return 500;
  }

  @Override
  public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
    return consolePlugin instanceof ArduinoConsolePlugin;
  }
}

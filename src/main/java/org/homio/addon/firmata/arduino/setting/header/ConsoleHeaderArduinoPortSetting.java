package org.homio.addon.firmata.arduino.setting.header;

import com.fazecast.jSerialComm.SerialPort;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginOptionsPort;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;

public class ConsoleHeaderArduinoPortSetting implements ConsoleHeaderSettingPlugin<SerialPort>, SettingPluginOptionsPort {

  @Override
  public Integer getMaxWidth() {
    return 155;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-project-diagram");
  }

  @Override
  public boolean isRequired() {
    return true;
  }
}

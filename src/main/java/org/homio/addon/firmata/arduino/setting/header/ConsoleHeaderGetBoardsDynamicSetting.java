package org.homio.addon.firmata.arduino.setting.header;

import org.homio.api.model.Icon;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;

public class ConsoleHeaderGetBoardsDynamicSetting implements DynamicConsoleHeaderContainerSettingPlugin {

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-cubes", "#1C9CB0");
  }
}

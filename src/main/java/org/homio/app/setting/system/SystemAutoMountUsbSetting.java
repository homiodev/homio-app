package org.homio.app.setting.system;

import lombok.extern.log4j.Log4j2;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class SystemAutoMountUsbSetting
  implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fab fa-usb");
  }

  @Override
  public int order() {
    return 400;
  }
}

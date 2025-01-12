package org.homio.app.setting.system;

import lombok.extern.log4j.Log4j2;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.app.LogService;
import org.homio.app.config.AppConfig;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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

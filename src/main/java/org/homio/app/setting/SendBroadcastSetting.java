package org.homio.app.setting;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.jetbrains.annotations.NotNull;

public class SendBroadcastSetting implements SettingPluginButton {

  @Override
  public int order() {
    return 0;
  }

  @Override
  public String getConfirmMsg() {
    return null;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fas fa-play");
  }
}

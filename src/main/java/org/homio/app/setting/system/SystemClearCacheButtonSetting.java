package org.homio.app.setting.system;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class SystemClearCacheButtonSetting
  implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public String getConfirmMsg() {
    return "W.CONFIRM.CLEAR_CACHE";
  }

  @Override
  public String getDialogColor() {
    return Color.ERROR_DIALOG;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fas fa-brush");
  }

  @Override
  public int order() {
    return 200;
  }
}

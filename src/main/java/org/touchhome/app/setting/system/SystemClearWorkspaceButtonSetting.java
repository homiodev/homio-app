package org.touchhome.app.setting.system;

import static org.touchhome.bundle.api.util.Constants.DANGER_COLOR;

import org.json.JSONObject;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginButton;

public class SystemClearWorkspaceButtonSetting implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public String getSubGroupKey() {
    return "WORKSPACE";
  }

  @Override
  public String getIconColor() {
    return DANGER_COLOR;
  }

  @Override
  public String getIcon() {
    return "fas fa-trash";
  }

  @Override
  public int order() {
    return 100;
  }
}

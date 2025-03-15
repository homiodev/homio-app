package org.homio.app.setting.dashboard;

import org.homio.api.setting.SettingType;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class WidgetBorderColorMenuSetting implements CoreSettingPlugin<String> {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.dashboard;
  }

  @Override
  public @NotNull String getSubGroupKey() {
    return "WIDGET";
  }

  @Override
  public @NotNull Class<String> getType() {
    return String.class;
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "#18576D";
  }

  @Override
  public @NotNull SettingType getSettingType() {
    return SettingType.ColorPicker;
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public boolean isReverted() {
    return true;
  }
}

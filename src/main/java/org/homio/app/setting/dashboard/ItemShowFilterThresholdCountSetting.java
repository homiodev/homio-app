package org.homio.app.setting.dashboard;

import org.homio.api.setting.SettingPluginSlider;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class ItemShowFilterThresholdCountSetting
  implements CoreSettingPlugin<Integer>, SettingPluginSlider {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.dashboard;
  }

  @Override
  public int getMin() {
    return 3;
  }

  @Override
  public int getMax() {
    return 20;
  }

  @Override
  public int defaultValue() {
    return 10;
  }

  @Override
  public int order() {
    return 350;
  }
}

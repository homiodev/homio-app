package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginSlider;

public class DashboardHorizontalBlockCountSetting implements CoreSettingPlugin<Integer>, SettingPluginSlider {

  @Override
  public GroupKey getGroupKey() {
    return GroupKey.dashboard;
  }

  @Override
  public String getSubGroupKey() {
    return "BLOCKS";
  }

  @Override
  public int defaultValue() {
    return 8;
  }

  @Override
  public Integer getMin() {
    return 1;
  }

  @Override
  public Integer getMax() {
    return 10;
  }

  @Override
  public int order() {
    return 100;
  }
}

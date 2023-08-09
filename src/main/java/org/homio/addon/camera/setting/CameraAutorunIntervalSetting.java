package org.homio.addon.camera.setting;

import org.homio.api.setting.SettingPluginSlider;

public class CameraAutorunIntervalSetting implements SettingPluginSlider {

  @Override
  public int order() {
    return 530;
  }

  @Override
  public Integer getMin() {
    return 1;
  }

  @Override
  public Integer getMax() {
    return 30;
  }

  @Override
  public int defaultValue() {
    return 5;
  }

  @Override
  public boolean isAdvanced() {
    return true;
  }
}

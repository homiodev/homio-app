package org.homio.addon.firmata.setting;

import org.homio.api.setting.SettingPluginSlider;

public class FirmataWatchDogIntervalSetting implements SettingPluginSlider {

  @Override
  public Integer getMin() {
    return 1;
  }

  @Override
  public Integer getMax() {
    return 60;
  }

  @Override
  public String getHeader() {
    return "Min";
  }

  @Override
  public int defaultValue() {
    return 10;
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

package org.homio.addon.camera.setting.onvif;

import org.homio.api.setting.SettingPluginTextInput;
import org.jetbrains.annotations.NotNull;

public class ScanOnvifHttpDefaultPasswordAuthSetting implements SettingPluginTextInput {

  @Override
  public @NotNull String getDefaultValue() {
    return "";
  }

  @Override
  public int order() {
    return 320;
  }

  @Override
  public boolean isAdvanced() {
    return true;
  }

  @Override
  public String group() {
    return "scan_onvif_http";
  }
}

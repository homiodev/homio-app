package org.homio.addon.camera.setting.onvif;

import org.homio.api.setting.SettingPluginTextInput;
import org.jetbrains.annotations.NotNull;

public class ScanOnvifHttpDefaultUserAuthSetting implements SettingPluginTextInput {

  @Override
  public @NotNull String getDefaultValue() {
    return "admin";
  }

  @Override
  public int order() {
    return 310;
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

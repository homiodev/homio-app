package org.homio.app.setting;

import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.route.UIRouteMicroController;

public class ScanDevicesSetting implements SettingPluginButton {

  @Override
  public int order() {
    return 0;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-qrcode", "#7482D0");
  }

  @Override
  public String getConfirmMsg() {
    return "TITLE.SCAN_DEVICES";
  }

  @Override
  public boolean isVisible(Context context) {
    return false;
  }

  @Override
  public String availableForRoute() {
    return UIRouteMicroController.ROUTE;
  }
}

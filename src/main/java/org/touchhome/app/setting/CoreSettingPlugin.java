package org.touchhome.app.setting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.bundle.api.setting.SettingPlugin;

public interface CoreSettingPlugin<T> extends SettingPlugin<T> {

  GroupKey getGroupKey();

  default String getSubGroupKey() {
    return "GENERAL";
  }

  @RequiredArgsConstructor
  enum GroupKey {
    dashboard("fas fa-tachometer-alt"),
    workspace("fas fa-map"),
    usb("fab fa-usb"),
    system("fas fa-tools");

    @Getter
    private final String icon;
  }
}

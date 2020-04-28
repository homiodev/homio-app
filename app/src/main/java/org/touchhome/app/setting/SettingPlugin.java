package org.touchhome.app.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.bundle.api.BundleSettingPlugin;

public interface SettingPlugin<T> extends BundleSettingPlugin<T> {

    GroupKey getGroupKey();

    default String getSubGroupKey() {
        return "GENERAL";
    }

    @AllArgsConstructor
    enum GroupKey {
        dashboard("fas fa-tachometer-alt"),
        workspace("fas fa-map"),
        usb("fab fa-usb"),
        system("fas fa-tools");

        @Getter
        private final String icon;
    }
}

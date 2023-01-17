package org.touchhome.app.setting;

import org.touchhome.bundle.api.setting.SettingPlugin;

public interface CoreSettingPlugin<T> extends SettingPlugin<T> {

    GroupKey getGroupKey();

    default String getSubGroupKey() {
        return "GENERAL";
    }

    enum GroupKey {
        dashboard,
        workspace,
        usb,
        system;
    }
}

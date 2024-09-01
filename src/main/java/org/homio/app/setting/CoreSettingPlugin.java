package org.homio.app.setting;

import org.homio.api.setting.SettingPlugin;
import org.jetbrains.annotations.NotNull;

public interface CoreSettingPlugin<T> extends SettingPlugin<T> {

    @NotNull
    GroupKey getGroupKey();

    @NotNull
    default String getSubGroupKey() {
        return "GENERAL";
    }

    enum GroupKey {
        dashboard,
        workspace,
        usb,
        system
    }
}

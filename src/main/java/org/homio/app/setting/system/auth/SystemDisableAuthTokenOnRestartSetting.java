package org.homio.app.setting.system.auth;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemDisableAuthTokenOnRestartSetting
        implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public @NotNull String getSubGroupKey() {
        return "AUTH";
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public int order() {
        return 300;
    }
}

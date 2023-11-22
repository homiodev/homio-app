package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemShowEntityUpdateTimeSetting
        implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 1110;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }
}

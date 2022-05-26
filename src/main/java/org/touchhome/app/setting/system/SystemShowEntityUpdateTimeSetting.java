package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;

public class SystemShowEntityUpdateTimeSetting implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 1110;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }
}

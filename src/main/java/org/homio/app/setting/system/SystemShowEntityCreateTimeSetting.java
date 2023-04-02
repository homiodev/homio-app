package org.homio.app.setting.system;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginBoolean;

public class SystemShowEntityCreateTimeSetting
        implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 1100;
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

package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;

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

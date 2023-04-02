package org.homio.app.setting.system;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginBoolean;

public class SystemShowConsoleSetting implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
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

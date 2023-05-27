package org.homio.app.setting.sidebar;

import org.homio.api.setting.SettingPluginBoolean;

public class SidebarColorMenuSetting implements SettingPluginBoolean {

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public int order() {
        return 0;
    }
}

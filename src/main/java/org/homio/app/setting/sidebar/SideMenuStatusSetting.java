package org.homio.app.setting.sidebar;

import org.homio.bundle.api.setting.SettingPluginBoolean;

public class SideMenuStatusSetting implements SettingPluginBoolean {

    @Override
    public int order() {
        return 2;
    }
}

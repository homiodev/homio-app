package org.touchhome.app.setting.sidebar;

import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;

public class SidebarColorMenuSetting implements BundleSettingPluginBoolean {

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public int order() {
        return 0;
    }
}

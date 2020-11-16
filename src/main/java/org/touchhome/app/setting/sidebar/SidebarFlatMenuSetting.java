package org.touchhome.app.setting.sidebar;

import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;

public class SidebarFlatMenuSetting implements BundleSettingPluginBoolean {

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public int order() {
        return 1;
    }
}

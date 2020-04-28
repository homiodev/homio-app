package org.touchhome.app.setting.sidebar;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class SidebarFlatMenuSetting implements BundleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 0;
    }
}

package org.touchhome.app.setting.sidebar;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class SidebarColorMenuSetting implements BundleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public String getDefaultValue() {
        return Boolean.TRUE.toString();
    }

    @Override
    public int order() {
        return 0;
    }
}

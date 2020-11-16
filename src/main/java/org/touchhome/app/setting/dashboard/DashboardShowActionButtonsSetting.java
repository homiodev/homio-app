package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;

public class DashboardShowActionButtonsSetting implements SettingPlugin<Boolean>, BundleSettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
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

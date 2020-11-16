package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;

public class SystemShowConsoleSetting implements SettingPlugin<Boolean>, BundleSettingPluginBoolean {

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

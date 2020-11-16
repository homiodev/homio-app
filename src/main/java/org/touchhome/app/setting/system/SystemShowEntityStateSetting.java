package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;

/**
 * Show BaseEntity CRUD
 */
public class SystemShowEntityStateSetting implements SettingPlugin<Boolean>, BundleSettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "EVENTS";
    }

    @Override
    public int order() {
        return 500;
    }
}

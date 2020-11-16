package org.touchhome.app.setting.system;

import org.json.JSONObject;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginButton;

public class SystemClearCacheButtonSetting implements SettingPlugin<JSONObject>, BundleSettingPluginButton {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getIconColor() {
        return "#BD1E1E";
    }

    @Override
    public String getIcon() {
        return "fas fa-trash";
    }

    @Override
    public int order() {
        return 200;
    }
}

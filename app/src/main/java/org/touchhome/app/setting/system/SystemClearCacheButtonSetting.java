package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;

public class SystemClearCacheButtonSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getIconColor() {
        return "text-danger";
    }

    @Override
    public String getIcon() {
        return "fas fa-trash";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 200;
    }
}

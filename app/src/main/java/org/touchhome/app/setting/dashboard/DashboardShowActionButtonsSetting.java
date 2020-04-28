package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.SettingPlugin;

public class DashboardShowActionButtonsSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getDefaultValue() {
        return Boolean.TRUE.toString();
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 300;
    }
}

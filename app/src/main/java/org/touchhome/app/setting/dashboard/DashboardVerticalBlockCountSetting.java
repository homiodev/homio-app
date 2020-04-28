package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.SettingPlugin;

public class DashboardVerticalBlockCountSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getSubGroupKey() {
        return "BLOCKS";
    }

    @Override
    public String getDefaultValue() {
        return "8";
    }

    @Override
    public String[] getAvailableValues() {
        return new String[]{"1", "10", "1"}; // min/max/step
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Slider;
    }

    @Override
    public int order() {
        return 200;
    }
}

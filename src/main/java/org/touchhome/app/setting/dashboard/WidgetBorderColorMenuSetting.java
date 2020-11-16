package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.SettingPlugin;

public class WidgetBorderColorMenuSetting implements SettingPlugin<String> {

    @Override
    public SettingPlugin.GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getSubGroupKey() {
        return "WIDGET";
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getDefaultValue() {
        return "#18576D";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}

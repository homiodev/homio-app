package org.homio.app.setting.dashboard;

import org.homio.api.ui.field.UIFieldType;
import org.homio.app.setting.CoreSettingPlugin;

public class WidgetBorderColorMenuSetting implements CoreSettingPlugin<String> {

    @Override
    public GroupKey getGroupKey() {
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
    public UIFieldType getSettingType() {
        return UIFieldType.ColorPicker;
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

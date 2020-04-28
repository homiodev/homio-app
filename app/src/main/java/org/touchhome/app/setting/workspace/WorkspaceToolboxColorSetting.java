package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.SettingPlugin;

public class WorkspaceToolboxColorSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public String getDefaultValue() {
        return "#F9F9F9";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 300;
    }
}

package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.SettingPlugin;

public class WorkspaceGridColorSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public String getDefaultValue() {
        return "#E65100";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 200;
    }
}

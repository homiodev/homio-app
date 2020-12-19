package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.CoreSettingPlugin;

public class WorkspaceToolboxColorSetting implements CoreSettingPlugin<String> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public Class<String> getType() {
        return String.class;
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

    @Override
    public boolean isReverted() {
        return true;
    }
}

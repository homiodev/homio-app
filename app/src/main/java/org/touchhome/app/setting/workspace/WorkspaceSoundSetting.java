package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.SettingPlugin;

public class WorkspaceSoundSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
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
        return 400;
    }
}

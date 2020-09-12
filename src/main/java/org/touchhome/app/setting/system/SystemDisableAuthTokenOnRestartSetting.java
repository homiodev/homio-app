package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;

public class SystemDisableAuthTokenOnRestartSetting implements SettingPlugin<Boolean> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "AUTH";
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

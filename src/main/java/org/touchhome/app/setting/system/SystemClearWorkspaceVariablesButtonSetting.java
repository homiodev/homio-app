package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;

public class SystemClearWorkspaceVariablesButtonSetting implements SettingPlugin<Void> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "WORKSPACE";
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

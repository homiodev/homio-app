package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;

/**
 * Show BaseEntity CRUD
 */
public class SystemShowEntityStateSetting implements SettingPlugin<Boolean> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "EVENTS";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 500;
    }
}

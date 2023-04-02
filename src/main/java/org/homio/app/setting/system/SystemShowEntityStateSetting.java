package org.homio.app.setting.system;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginBoolean;

/** Show BaseEntity CRUD */
public class SystemShowEntityStateSetting
        implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "EVENTS";
    }

    @Override
    public int order() {
        return 500;
    }
}

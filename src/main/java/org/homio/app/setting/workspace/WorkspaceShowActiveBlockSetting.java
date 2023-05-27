package org.homio.app.setting.workspace;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;

public class WorkspaceShowActiveBlockSetting
        implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public boolean defaultValue() {
        return false;
    }

    @Override
    public int order() {
        return 900;
    }
}

package org.homio.app.setting.workspace;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginBoolean;

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

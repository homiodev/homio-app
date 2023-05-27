package org.homio.app.setting.system;

import java.util.Set;
import org.homio.api.setting.SettingPluginTextSet;
import org.homio.app.setting.CoreSettingPlugin;

public class SystemAddonRepositoriesSetting
    implements CoreSettingPlugin<Set<String>>, SettingPluginTextSet {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public int order() {
        return 2000;
    }

    @Override
    public String[] defaultValue() {
        return new String[]{"https://github.com/homiodev/addon-parent"};
    }
}

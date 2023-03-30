package org.touchhome.app.setting.system;

import java.util.Set;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginTextSet;

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
        return new String[]{"https://github.com/touchhome/addon-parent"};
    }
}

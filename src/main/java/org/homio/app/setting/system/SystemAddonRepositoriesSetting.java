package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginTextSet;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class SystemAddonRepositoriesSetting
        implements CoreSettingPlugin<Set<String>>, SettingPluginTextSet {

    @Override
    public @NotNull GroupKey getGroupKey() {
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

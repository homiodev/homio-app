package org.homio.app.setting.system;

import java.util.Set;

import org.homio.api.setting.SettingPluginTextSet;
import org.homio.api.util.Lang;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemPlaceSetting implements CoreSettingPlugin<Set<String>>, SettingPluginTextSet {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String[] defaultValue() {
        return Lang.getServerMessage("DEFAULT_PLACES").split(";");
    }
}

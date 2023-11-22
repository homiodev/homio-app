package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginTextSet;
import org.homio.api.util.Lang;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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

package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginOptionsEnum;
import org.homio.api.util.Lang;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemLanguageSetting
        implements CoreSettingPlugin<Lang>, SettingPluginOptionsEnum<Lang> {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public @NotNull Class<Lang> getType() {
        return Lang.class;
    }

    @Override
    public int order() {
        return 600;
    }
}

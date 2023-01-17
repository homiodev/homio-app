package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginOptionsEnum;
import org.touchhome.common.util.Lang;

public class SystemLanguageSetting
        implements CoreSettingPlugin<Lang>, SettingPluginOptionsEnum<Lang> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public Class<Lang> getType() {
        return Lang.class;
    }

    @Override
    public int order() {
        return 600;
    }
}

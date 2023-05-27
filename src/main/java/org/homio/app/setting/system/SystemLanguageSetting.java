package org.homio.app.setting.system;

import org.homio.api.setting.SettingPluginOptionsEnum;
import org.homio.api.util.Lang;
import org.homio.app.setting.CoreSettingPlugin;

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

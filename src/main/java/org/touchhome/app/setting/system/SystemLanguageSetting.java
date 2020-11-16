package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginSelectBoxEnum;
import org.touchhome.bundle.api.ui.Lang;

public class SystemLanguageSetting implements SettingPlugin<Lang>,
        BundleSettingPluginSelectBoxEnum<Lang> {

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

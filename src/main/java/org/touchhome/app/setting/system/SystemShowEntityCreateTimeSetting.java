package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;
import org.touchhome.common.util.CommonUtils;

import java.nio.file.Path;

public class SystemShowEntityCreateTimeSetting implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 1100;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }
}

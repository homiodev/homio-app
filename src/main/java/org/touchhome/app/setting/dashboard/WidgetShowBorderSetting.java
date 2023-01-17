package org.touchhome.app.setting.dashboard;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;

public class WidgetShowBorderSetting implements CoreSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getSubGroupKey() {
        return "WIDGET";
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public int order() {
        return 400;
    }
}

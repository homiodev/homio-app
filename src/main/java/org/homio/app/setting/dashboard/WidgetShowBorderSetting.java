package org.homio.app.setting.dashboard;

import org.homio.api.setting.SettingPluginBoolean;
import org.homio.app.setting.CoreSettingPlugin;

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

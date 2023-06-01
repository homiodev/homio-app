package org.homio.app.setting.console;

import org.homio.api.setting.SettingPluginToggle;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;

public class ShowInlineReadOnlyConsoleConsoleHeaderSetting implements ConsoleHeaderSettingPlugin<Boolean>, SettingPluginToggle {

    @Override
    public String getIcon() {
        return "fas fa-file-code";
    }

    @Override
    public String getToggleIcon() {
        return "far fa-file-code";
    }

    @Override
    public boolean isStorable() {
        return false;
    }
}

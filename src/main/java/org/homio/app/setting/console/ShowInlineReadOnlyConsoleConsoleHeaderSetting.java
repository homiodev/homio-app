package org.homio.app.setting.console;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginToggle;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;

public class ShowInlineReadOnlyConsoleConsoleHeaderSetting implements ConsoleHeaderSettingPlugin<Boolean>, SettingPluginToggle {

    @Override
    public Icon getIcon() {
        return new Icon("fas fa-file-code");
    }

    @Override
    public Icon getToggleIcon() {
        return new Icon("far fa-file-code");
    }

    @Override
    public boolean isStorable() {
        return false;
    }
}

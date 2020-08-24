package org.touchhome.app.setting.console;

import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.console.ConsolePlugin;

public class ConsoleFitContentSetting implements BundleConsoleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public String[] pages() {
        return new String[0];
    }

    @Override
    public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
        return consolePlugin.hasFitContentSetting();
    }
}

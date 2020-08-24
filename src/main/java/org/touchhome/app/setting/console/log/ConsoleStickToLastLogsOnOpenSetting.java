package org.touchhome.app.setting.console.log;

import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;

public class ConsoleStickToLastLogsOnOpenSetting implements BundleConsoleSettingPlugin<Boolean> {

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
        return new String[]{"log"};
    }
}

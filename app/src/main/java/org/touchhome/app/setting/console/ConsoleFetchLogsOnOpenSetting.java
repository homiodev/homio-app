package org.touchhome.app.setting.console;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ConsoleFetchLogsOnOpenSetting implements BundleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 600;
    }
}

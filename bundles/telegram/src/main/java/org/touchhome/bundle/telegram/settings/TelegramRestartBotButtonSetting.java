package org.touchhome.bundle.telegram.settings;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class TelegramRestartBotButtonSetting implements BundleSettingPlugin {

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 300;
    }
}

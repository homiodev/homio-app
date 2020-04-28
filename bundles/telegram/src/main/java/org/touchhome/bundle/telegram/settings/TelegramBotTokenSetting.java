package org.touchhome.bundle.telegram.settings;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class TelegramBotTokenSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public int order() {
        return 200;
    }
}

package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class Nrf24i01StatusMessageSetting implements BundleSettingPlugin<String> {
    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 0;
    }
}

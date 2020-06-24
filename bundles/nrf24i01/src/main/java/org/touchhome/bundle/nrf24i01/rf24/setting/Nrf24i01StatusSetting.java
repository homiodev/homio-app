package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.model.DeviceStatus;

public class Nrf24i01StatusSetting implements BundleSettingPlugin<DeviceStatus> {
    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 2;
    }
}

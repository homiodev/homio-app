package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeResetNetworkButtonSetting implements BundleSettingPlugin {

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 1500;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

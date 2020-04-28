package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeNetworkKeySetting implements BundleSettingPlugin<String> {

    @Override
    public String getDefaultValue() {
        return "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public int order() {
        return 1300;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

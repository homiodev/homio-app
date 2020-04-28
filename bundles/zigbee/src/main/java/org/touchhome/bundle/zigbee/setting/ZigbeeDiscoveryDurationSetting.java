package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeDiscoveryDurationSetting implements BundleSettingPlugin<Integer> {

    @Override
    public String getDefaultValue() {
        return "254";
    }

    @Override
    public String[] getAvailableValues() {
        return new String[]{"60", "254", "1"};
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Slider;
    }

    @Override
    public int order() {
        return 200;
    }
}

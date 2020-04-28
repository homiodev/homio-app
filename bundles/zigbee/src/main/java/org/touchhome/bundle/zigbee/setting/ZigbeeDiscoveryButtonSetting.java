package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeDiscoveryButtonSetting implements BundleSettingPlugin<String> {

    @Override
    public String getIcon() {
        return "fas fa-search-location";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 100;
    }
}

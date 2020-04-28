package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeePanIdSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Integer;
    }

    @Override
    public String getDefaultValue() {
        return "65535";
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

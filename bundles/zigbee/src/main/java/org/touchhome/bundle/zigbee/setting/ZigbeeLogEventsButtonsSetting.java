package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeLogEventsButtonsSetting implements BundleSettingPlugin<Boolean> {

    @Override
    public String getDefaultValue() {
        return Boolean.TRUE.toString();
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 300;
    }
}

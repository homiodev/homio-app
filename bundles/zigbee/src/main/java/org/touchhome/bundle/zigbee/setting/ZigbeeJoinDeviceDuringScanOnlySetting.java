package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class ZigbeeJoinDeviceDuringScanOnlySetting implements BundleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

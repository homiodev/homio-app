package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudUseHomeNetworkWhenPossibleSetting implements BundleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public String getDefaultValue() {
        return Boolean.TRUE.toString();
    }

    @Override
    public int order() {
        return 50;
    }
}

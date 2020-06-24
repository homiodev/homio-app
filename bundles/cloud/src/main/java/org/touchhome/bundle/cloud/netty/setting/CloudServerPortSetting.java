package org.touchhome.bundle.cloud.netty.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerPortSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Integer;
    }

    @Override
    public String getDefaultValue() {
        return "8888";
    }

    @Override
    public int order() {
        return 45;
    }
}

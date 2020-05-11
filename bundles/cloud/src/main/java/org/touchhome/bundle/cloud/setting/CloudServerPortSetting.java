package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerPortSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
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

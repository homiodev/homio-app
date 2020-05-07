package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudHomeIpAddressSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public String getDefaultValue() {
        return "http://localhost:9111";
    }

    @Override
    public int order() {
        return 40;
    }
}

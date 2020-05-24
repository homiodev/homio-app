package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerUrlSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public String getDefaultValue() {
        return "https://touchhome.org";
    }

    @Override
    public int order() {
        return 40;
    }
}

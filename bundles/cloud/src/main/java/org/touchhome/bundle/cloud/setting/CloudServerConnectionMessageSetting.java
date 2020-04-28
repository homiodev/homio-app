package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerConnectionMessageSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 30;
    }
}

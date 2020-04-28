package org.touchhome.bundle.cloud.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerRestartSetting implements BundleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 10;
    }
}

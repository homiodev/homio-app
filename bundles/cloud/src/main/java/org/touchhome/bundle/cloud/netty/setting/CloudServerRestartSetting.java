package org.touchhome.bundle.cloud.netty.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class CloudServerRestartSetting implements BundleSettingPlugin<Void> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Button;
    }

    @Override
    public int order() {
        return 10;
    }
}

package org.touchhome.app.videoStream.setting.rtsp;

import org.touchhome.bundle.api.setting.SettingPluginSlider;

public class RtspDiscoveryIpAddressPingTimeoutSetting implements SettingPluginSlider {

    @Override
    public int order() {
        return 2000;
    }

    @Override
    public Integer getMin() {
        return 1;
    }

    @Override
    public Integer getMax() {
        return 5000;
    }

    @Override
    public int defaultValue() {
        return 500;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

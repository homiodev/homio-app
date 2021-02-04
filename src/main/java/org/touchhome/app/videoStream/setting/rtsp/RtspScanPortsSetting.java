package org.touchhome.app.videoStream.setting.rtsp;

import org.touchhome.bundle.api.setting.SettingPluginIntegerSet;

public class RtspScanPortsSetting implements SettingPluginIntegerSet {

    @Override
    public int order() {
        return 900;
    }

    @Override
    public int[] defaultValue() {
        return new int[]{554, 8554};
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

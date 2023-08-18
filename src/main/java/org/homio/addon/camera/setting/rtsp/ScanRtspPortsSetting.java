package org.homio.addon.camera.setting.rtsp;

import org.homio.api.setting.SettingPluginIntegerSet;

public class ScanRtspPortsSetting implements SettingPluginIntegerSet {

    @Override
    public int order() {
        return 110;
    }

    @Override
    public int[] defaultValue() {
        return new int[]{554, 8554};
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }

    @Override
    public String group() {
        return "scan_rtsp";
    }
}

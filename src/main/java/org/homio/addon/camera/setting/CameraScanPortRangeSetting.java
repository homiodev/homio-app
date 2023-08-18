package org.homio.addon.camera.setting;

import static org.homio.api.util.CommonUtils.MACHINE_IP_ADDRESS;

import java.util.Set;

import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginTextSet;
import org.homio.hquery.hardware.network.NetworkDescription;

public class CameraScanPortRangeSetting implements SettingPluginTextSet, SettingPlugin<Set<String>> {

    @Override
    public int order() {
        return 12;
    }

    @Override
    public String getPattern() {
        return NetworkDescription.IP_RANGE_PATTERN;
    }

    @Override
    public String[] defaultValue() {
        return new String[]{MACHINE_IP_ADDRESS.substring(0, MACHINE_IP_ADDRESS.lastIndexOf(".") + 1) + "0-255"};
    }
}

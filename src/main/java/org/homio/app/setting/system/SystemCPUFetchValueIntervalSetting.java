package org.homio.app.setting.system;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginSlider;

public class SystemCPUFetchValueIntervalSetting
        implements CoreSettingPlugin<Integer>, SettingPluginSlider {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "CPU";
    }

    @Override
    public int defaultValue() {
        return 30;
    }

    @Override
    public Integer getMin() {
        return 10;
    }

    @Override
    public Integer getMax() {
        return 60;
    }

    @Override
    public String getHeader() {
        return "min";
    }

    @Override
    public int order() {
        return 1500;
    }
}

package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginSlider;

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
        return 1;
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

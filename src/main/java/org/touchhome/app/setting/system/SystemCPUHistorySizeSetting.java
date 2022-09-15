package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginSlider;

public class SystemCPUHistorySizeSetting implements CoreSettingPlugin<Integer>, SettingPluginSlider {

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
        return 720;
    }

    @Override
    public Integer getMin() {
        return 100;
    }

    @Override
    public Integer getMax() {
        return 10000;
    }

    @Override
    public Integer getStep() {
        return 10;
    }

    @Override
    public String getHeader() {
        return "min";
    }

    @Override
    public int order() {
        return 1400;
    }
}

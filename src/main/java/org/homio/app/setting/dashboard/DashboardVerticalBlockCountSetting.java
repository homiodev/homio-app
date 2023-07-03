package org.homio.app.setting.dashboard;

import org.homio.api.setting.SettingPluginSlider;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class DashboardVerticalBlockCountSetting
    implements CoreSettingPlugin<Integer>, SettingPluginSlider {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public @NotNull String getSubGroupKey() {
        return "BLOCKS";
    }

    @Override
    public Integer getMin() {
        return 1;
    }

    @Override
    public Integer getMax() {
        return 10;
    }

    @Override
    public int defaultValue() {
        return 8;
    }

    @Override
    public int order() {
        return 200;
    }
}

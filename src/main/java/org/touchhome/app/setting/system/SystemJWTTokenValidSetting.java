package org.touchhome.app.setting.system;

import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginSlider;

public class SystemJWTTokenValidSetting implements SettingPlugin<Integer>, BundleSettingPluginSlider {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "AUTH";
    }

    @Override
    public int defaultValue() {
        return 30;
    }

    @Override
    public int getMin() {
        return 30;
    }

    @Override
    public int getMax() {
        return 1440;
    }

    @Override
    public String getHeader() {
        return "min";
    }

    @Override
    public int order() {
        return 400;
    }
}
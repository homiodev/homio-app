package org.touchhome.bundle.raspberry.settings;

import org.touchhome.bundle.api.BundleSettingPlugin;

public class RaspberryOneWireIntervalSetting implements BundleSettingPlugin<Integer> {

    @Override
    public String getDefaultValue() {
        return "30";
    }

    @Override
    public String[] getAvailableValues() {
        return new String[]{"10", "120", "1", "S"};
    }

    @Override
    public BundleSettingPlugin.SettingType getSettingType() {
        return BundleSettingPlugin.SettingType.Slider;
    }

    @Override
    public int order() {
        return 100;
    }
}

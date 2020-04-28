package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZigbeeMeshUpdatePeriodSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public String getDefaultValue() {
        return "86400";
    }

    @Override
    public List<Option> loadAvailableValues() {
        return new ArrayList<>(Arrays.asList(
                Option.of("0", "NEVER"),
                Option.of("300", "5 Minutes"),
                Option.of("1800", "30 Minutes"),
                Option.of("3600", "1 Hour"),
                Option.of("21600", "6 Minutes"),
                Option.of("86400", "1 Day"),
                Option.of("604800", "1 Week")));
    }

    @Override
    public int order() {
        return 1100;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }

    @Override
    public Integer parseValue(String value) {
        return Integer.parseInt(value);
    }
}

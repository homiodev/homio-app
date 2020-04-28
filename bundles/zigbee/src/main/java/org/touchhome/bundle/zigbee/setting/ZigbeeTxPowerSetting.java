package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

/**
 * <option value="8">High</option>
 * <option value="0">Normal</option>
 */
public class ZigbeeTxPowerSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }

    @Override
    public List<Option> loadAvailableValues() {
        return Option.range(0, 8);
    }

    @Override
    public Integer parseValue(String value) {
        return Integer.parseInt(value);
    }

    @Override
    public int order() {
        return 1400;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

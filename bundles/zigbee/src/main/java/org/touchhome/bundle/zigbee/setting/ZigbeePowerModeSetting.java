package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

public class ZigbeePowerModeSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public String getDefaultValue() {
        return "1";
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.list(Option.of("0", "Normal"), Option.of("1", "Boost"));
    }

    @Override
    public Integer parseValue(EntityContext entityContext, String value) {
        return Integer.parseInt(value);
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

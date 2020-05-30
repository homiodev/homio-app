package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZigbeePortBaudSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public String getDefaultValue() {
        return "115200";
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return new ArrayList<>(Arrays.asList(Option.key("38400"), Option.key("57600"), Option.key("115200")));
    }

    @Override
    public Integer parseValue(EntityContext entityContext, String value) {
        return Integer.parseInt(value);
    }

    @Override
    public int order() {
        return 750;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ZigbeeChannelIdSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public Integer parseValue(String value) {
        return Integer.parseInt(value);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }

    @Override
    public List<Option> loadAvailableValues() {
        List<Option> options = new ArrayList<>();
        options.add(Option.of("0", "AUTO"));
        options.addAll(IntStream.range(11, 25)
                .mapToObj(value -> Option.of(String.valueOf(value), "Channel " + value)).collect(Collectors.toList()));
        return options;
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

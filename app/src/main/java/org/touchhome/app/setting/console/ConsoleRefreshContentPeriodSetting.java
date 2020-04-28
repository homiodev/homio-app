package org.touchhome.app.setting.console;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsoleRefreshContentPeriodSetting implements BundleSettingPlugin<Integer> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }

    @Override
    public String getIcon() {
        return "far fa-clock";
    }

    @Override
    public List<Option> loadAvailableValues() {
        return new ArrayList<>(Arrays.asList(
                Option.of("0", "time.NEVER"),
                Option.of("5", "time.SEC_5"),
                Option.of("10", "time.SEC_10"),
                Option.of("30", "time.SEC_30"),
                Option.of("60", "time.SEC_60")));
    }

    @Override
    public int order() {
        return 700;
    }
}

package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.nrf24i01.rf24.options.DataRate;

import java.util.List;

public class Nrf24i01DataRateSetting implements BundleSettingPlugin<DataRate> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DataRate parseValue(String value) {
        return StringUtils.isEmpty(value) ? null : DataRate.valueOf(value);
    }

    @Override
    public String getDefaultValue() {
        return DataRate.RF24_250KBPS.name();
    }

    @Override
    public List<Option> loadAvailableValues() {
        return Option.list(DataRate.class);
    }
}

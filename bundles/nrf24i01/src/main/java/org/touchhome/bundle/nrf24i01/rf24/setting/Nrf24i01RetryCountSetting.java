package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.nrf24i01.rf24.options.CRCSize;
import org.touchhome.bundle.nrf24i01.rf24.options.RetryCount;

import java.util.List;

public class Nrf24i01RetryCountSetting implements BundleSettingPlugin<RetryCount> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public RetryCount parseValue(String value) {
        return StringUtils.isEmpty(value) ? null : RetryCount.valueOf(value);
    }

    @Override
    public String getDefaultValue() {
        return RetryCount.RETRY_15.name();
    }

    @Override
    public List<Option> loadAvailableValues() {
        return Option.list(RetryCount.class);
    }
}

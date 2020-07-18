package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.apache.commons.lang3.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.nrf24i01.rf24.options.PALevel;

import java.util.List;

public class Nrf24i01PALevelSetting implements BundleSettingPlugin<PALevel> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public PALevel parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null : PALevel.valueOf(value);
    }

    @Override
    public String getDefaultValue() {
        return PALevel.RF24_PA_MIN.name();
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.list(PALevel.class);
    }
}

package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.nrf24i01.rf24.options.CRCSize;

import java.util.List;

public class Nrf24i01CrcSizeSetting implements BundleSettingPlugin<CRCSize> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public CRCSize parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null : CRCSize.valueOf(value);
    }

    @Override
    public String getDefaultValue() {
        return CRCSize.ENABLE_8_BITS.name();
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.list(CRCSize.class);
    }
}

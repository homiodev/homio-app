package org.touchhome.bundle.nrf24i01.rf24.setting;

import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;

public class Nrf24i01StatusSetting implements BundleSettingPlugin<DeviceStatus> {
    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public DeviceStatus parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null : DeviceStatus.valueOf(value);
    }
}

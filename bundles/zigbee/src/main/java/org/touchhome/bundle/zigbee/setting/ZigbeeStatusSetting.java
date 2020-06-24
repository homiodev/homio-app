package org.touchhome.bundle.zigbee.setting;

import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;

public class ZigbeeStatusSetting implements BundleSettingPlugin<DeviceStatus> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public String getDefaultValue() {
        return DeviceStatus.UNKNOWN.toString();
    }

    @Override
    public DeviceStatus parseValue(EntityContext entityContext, String value) {
        return value == null ? DeviceStatus.UNKNOWN : DeviceStatus.valueOf(value);
    }

    @Override
    public int order() {
        return 400;
    }
}

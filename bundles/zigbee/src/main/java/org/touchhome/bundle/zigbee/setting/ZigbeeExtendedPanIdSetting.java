package org.touchhome.bundle.zigbee.setting;

import com.zsmartsystems.zigbee.ExtendedPanId;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;

public class ZigbeeExtendedPanIdSetting implements BundleSettingPlugin<ExtendedPanId> {

    @Override
    public String getDefaultValue() {
        return "0000000000000000";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public ExtendedPanId parseValue(EntityContext entityContext, String value) {
        return new ExtendedPanId(value);
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}

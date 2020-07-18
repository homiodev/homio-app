package org.touchhome.bundle.cloud.netty.setting;

import org.apache.commons.lang3.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.cloud.netty.impl.ServerConnectionStatus;

public class CloudServerConnectionStatusSetting implements BundleSettingPlugin<ServerConnectionStatus> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Info;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public ServerConnectionStatus parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null : ServerConnectionStatus.valueOf(value);
    }
}

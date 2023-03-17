package org.touchhome.app.setting.system.cloud;

import org.touchhome.app.service.cloud.SshTunnelCloudProviderService;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.service.CloudProviderService;
import org.touchhome.bundle.api.setting.SettingPluginOptionsBean;

public class SystemCloudProviderSetting implements CoreSettingPlugin<CloudProviderService>,
    SettingPluginOptionsBean<CloudProviderService> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "CLOUD";
    }

    @Override
    public Class<CloudProviderService> getType() {
        return CloudProviderService.class;
    }

    @Override
    public String getDefaultValue() {
        return SshTunnelCloudProviderService.class.getSimpleName();
    }

    @Override
    public int order() {
        return 700;
    }
}

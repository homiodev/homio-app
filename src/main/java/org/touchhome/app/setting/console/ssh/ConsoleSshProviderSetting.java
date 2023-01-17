package org.touchhome.app.setting.console.ssh;

import org.touchhome.app.service.ssh.TmateSshProvider;
import org.touchhome.bundle.api.service.SshProviderService;
import org.touchhome.bundle.api.setting.SettingPluginOptionsBean;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleSshProviderSetting
        implements ConsoleSettingPlugin<SshProviderService>,
                SettingPluginOptionsBean<SshProviderService> {

    @Override
    public Class<SshProviderService> getType() {
        return SshProviderService.class;
    }

    @Override
    public String getDefaultValue() {
        return TmateSshProvider.class.getSimpleName();
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public String[] pages() {
        return new String[] {"ssh"};
    }
}

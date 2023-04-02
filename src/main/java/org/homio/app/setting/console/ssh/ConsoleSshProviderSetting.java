package org.homio.app.setting.console.ssh;

import org.homio.app.service.ssh.TmateSshProvider;
import org.homio.bundle.api.service.SshProviderService;
import org.homio.bundle.api.setting.SettingPluginOptionsBean;
import org.homio.bundle.api.setting.console.ConsoleSettingPlugin;

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

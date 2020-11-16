package org.touchhome.app.setting.console.ssh;

import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.app.service.ssh.impl.TmateSshProvider;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;


import java.util.List;

public class ConsoleSshProviderSetting implements BundleConsoleSettingPlugin<SshProvider> {

    @Override
    public Class<SshProvider> getType() {
        return SshProvider.class;
    }

    @Override
    public String getDefaultValue() {
        return TmateSshProvider.class.getSimpleName();
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxDynamic;
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.simpleNamelist(entityContext.getBeansOfType(SshProvider.class));
    }

    @Override
    public SshProvider parseValue(EntityContext entityContext, String value) {
        return entityContext.getBeansOfType(SshProvider.class).stream().filter(p -> p.getClass().getSimpleName().equals(value)).findAny()
                .orElseThrow(() -> new NotFoundException("Unable to find ssh provider with name: " + value));
    }
}

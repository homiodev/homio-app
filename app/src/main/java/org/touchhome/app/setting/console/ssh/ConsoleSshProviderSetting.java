package org.touchhome.app.setting.console.ssh;

import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.app.service.ssh.impl.TmateSshProvider;
import org.touchhome.bundle.api.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.List;
import java.util.stream.Collectors;

public class ConsoleSshProviderSetting implements BundleConsoleSettingPlugin<SshProvider> {

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
        return entityContext.getBeansOfType(SshProvider.class)
                .stream().map(zb -> Option.key(zb.getClass().getSimpleName())).collect(Collectors.toList());
    }

    @Override
    public SshProvider parseValue(EntityContext entityContext, String value) {
        return entityContext.getBean(value, SshProvider.class);
    }
}

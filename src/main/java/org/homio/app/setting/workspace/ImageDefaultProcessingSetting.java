package org.homio.app.setting.workspace;

import org.homio.api.service.ImageProviderService;
import org.homio.api.setting.SettingPluginOptionsBean;
import org.homio.app.service.image.AwtImageProviderService;
import org.homio.app.setting.CoreSettingPlugin;

public class ImageDefaultProcessingSetting implements
    CoreSettingPlugin<ImageProviderService>,
    SettingPluginOptionsBean<ImageProviderService> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public Class<ImageProviderService> getType() {
        return ImageProviderService.class;
    }

    @Override
    public String getDefaultValue() {
        return AwtImageProviderService.class.getSimpleName();
    }

    @Override
    public int order() {
        return 100;
    }
}

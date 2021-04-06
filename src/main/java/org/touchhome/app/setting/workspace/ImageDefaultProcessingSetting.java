package org.touchhome.app.setting.workspace;

import org.touchhome.app.service.image.AwtImageProviderService;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.service.ImageProviderService;
import org.touchhome.bundle.api.setting.SettingPluginOptionsBean;

public class ImageDefaultProcessingSetting implements CoreSettingPlugin<ImageProviderService>, SettingPluginOptionsBean<ImageProviderService> {

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

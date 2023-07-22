package org.homio.app.setting.workspace;

import org.homio.api.service.ImageProviderService;
import org.homio.api.setting.SettingPluginOptionsBean;
import org.homio.app.service.image.AwtImageProviderService;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class ImageDefaultProcessingSetting implements
    CoreSettingPlugin<ImageProviderService>,
    SettingPluginOptionsBean<ImageProviderService> {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public @NotNull Class<ImageProviderService> getType() {
        return ImageProviderService.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return AwtImageProviderService.class.getSimpleName();
    }

    @Override
    public int order() {
        return 100;
    }
}

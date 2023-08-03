package org.homio.addon.tuya;

import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginToggle;
import org.jetbrains.annotations.NotNull;

public class TuyaEntityCompactModeSetting implements SettingPluginToggle {

    @Override
    public Class<? extends BaseEntity> availableForEntity() {
        return TuyaDeviceEntity.class;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-minimize");
    }

    @Override
    public @NotNull Icon getToggleIcon() {
        return new Icon("fas fa-maximize");
    }

    @Override
    public boolean isVisible(EntityContext entityContext) {
        return false;
    }
}

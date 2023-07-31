package org.homio.addon.tuya;

import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.jetbrains.annotations.NotNull;

public class ScanTuyaDevicesSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-qrcode", "#ED3E58");
    }

    @Override
    public String getConfirmMsg() {
        return "TUYA.SCAN_DEVICES";
    }

    @Override
    public boolean isVisible(EntityContext entityContext) {
        return false;
    }

    @Override
    public Class<? extends BaseEntity> availableForEntity() {
        return TuyaDeviceEntity.class;
    }
}

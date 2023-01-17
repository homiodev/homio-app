package org.touchhome.app.setting;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.types.MicroControllerBaseEntity;
import org.touchhome.bundle.api.setting.SettingPluginButton;

public class ScanMicroControllersSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String getIcon() {
        return "fas fa-qrcode";
    }

    @Override
    public String getIconColor() {
        return "#7482D0";
    }

    @Override
    public String getConfirmMsg() {
        return "TITLE.SCAN_CONTROLLERS";
    }

    @Override
    public boolean isVisible(EntityContext entityContext) {
        return false;
    }

    @Override
    public Class<? extends BaseEntity> availableForEntity() {
        return MicroControllerBaseEntity.class;
    }
}

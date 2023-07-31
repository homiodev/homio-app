package org.homio.app.setting;

import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;

public class ScanDevicesSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Icon getIcon() {
        return new Icon("fas fa-qrcode", "#7482D0");
    }

    @Override
    public String getConfirmMsg() {
        return "TITLE.SCAN_DEVICES";
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

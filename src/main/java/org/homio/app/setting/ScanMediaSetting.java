package org.homio.app.setting;

import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;

public class ScanMediaSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Icon getIcon() {
        return new Icon("fas fa-qrcode", "#A629A4");
    }

    @Override
    public String getConfirmMsg() {
        return "TITLE.SCAN_MEDIA";
    }

    @Override
    public boolean isVisible(Context context) {
        return false;
    }

    @Override
    public Class<? extends BaseEntity> availableForEntity() {
        return MediaEntity.class;
    }
}

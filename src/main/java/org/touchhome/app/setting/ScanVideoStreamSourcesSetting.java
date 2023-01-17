package org.touchhome.app.setting;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.setting.SettingPluginButton;
import org.touchhome.bundle.api.video.BaseVideoStreamEntity;

public class ScanVideoStreamSourcesSetting implements SettingPluginButton {

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
        return "#ED703E";
    }

    @Override
    public String getConfirmMsg() {
        return "TITLE.SCAN_VIDEO_STREAMS";
    }

    @Override
    public boolean isVisible(EntityContext entityContext) {
        return false;
    }

    @Override
    public Class<? extends BaseEntity> availableForEntity() {
        return BaseVideoStreamEntity.class;
    }
}

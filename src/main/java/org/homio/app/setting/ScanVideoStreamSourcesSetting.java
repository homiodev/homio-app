package org.homio.app.setting;

import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.video.BaseVideoStreamEntity;
import org.jetbrains.annotations.NotNull;

public class ScanVideoStreamSourcesSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-qrcode", "#ED703E");
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

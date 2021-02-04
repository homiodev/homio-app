package org.touchhome.app.videoStream.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.touchhome.app.videoStream.scanner.VideoStreamScanner;
import org.touchhome.app.videoStream.ui.CameraAction;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.service.scan.BaseBeansItemsDiscovery;
import org.touchhome.bundle.api.ui.UISidebarButton;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.image.UIFieldImage;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Transient;
import java.util.List;

@Log4j2
@Setter
@Getter
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@UISidebarMenu(icon = "fas fa-video", order = 1, parent = UISidebarMenu.TopSidebarMenu.MEDIA, bg = "#5950A7", allowCreateNewItems = true)
@UISidebarButton(buttonIcon = "fas fa-qrcode", buttonIconColor = "#ED703E",
        buttonTitle = "TITLE.SCAN_VIDEO_STREAMS", handlerClass = BaseVideoStreamEntity.VideoStreamDiscovery.class)
public abstract class BaseVideoStreamEntity<T extends BaseVideoStreamEntity> extends DeviceBaseEntity<T> {

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String hlsStreamUrl; // for widget

    public abstract List<CameraAction> getActions(boolean fetchValues);

    public abstract byte[] getSnapshot();

    static class VideoStreamDiscovery extends BaseBeansItemsDiscovery {

        public VideoStreamDiscovery() {
            super(VideoStreamScanner.class);
        }
    }

    public static class UpdateSnapshotActionHandler implements UIActionHandler {

        @Override
        public ActionResponseModel apply(EntityContext entityContext, JSONObject params) {
            BaseVideoStreamEntity entity = entityContext.getEntity(params.getString("entityID"));
            entity.fireUpdateSnapshot(entityContext, params);
            return null;
        }
    }

    protected abstract void fireUpdateSnapshot(EntityContext entityContext, JSONObject params);
}

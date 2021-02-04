package org.touchhome.app.videoStream.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.videoStream.handler.RtspStreamHandler;
import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class RtspVideoStreamEntity extends BaseFFmpegStreamEntity<RtspVideoStreamEntity> {

    public static final String PREFIX = "rtsp_";

    @Override
    @UIField(order = 5, label = "rtspUrl")
    @RestartHandlerOnChange
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public RtspStreamHandler createCameraHandler(EntityContext entityContext) {
        return new RtspStreamHandler(this, entityContext);
    }

    @Override
    public String toString() {
        return "rtsp:" + getIeeeAddress();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}

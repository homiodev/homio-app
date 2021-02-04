package org.touchhome.app.videoStream.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.videoStream.handler.BaseCameraHandler;
import org.touchhome.app.videoStream.handler.HlsStreamHandler;
import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class HlsVideoStreamEntity extends BaseFFmpegStreamEntity<HlsVideoStreamEntity> {

    public static final String PREFIX = "hls_";

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public boolean isStart() {
        return super.isStart();
    }

    @Override
    @UIField(order = 5, label = "hlsUrl")
    @RestartHandlerOnChange
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public String toString() {
        return "hls:" + getIeeeAddress();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public BaseCameraHandler createCameraHandler(EntityContext entityContext) {
        return new HlsStreamHandler(this, entityContext);
    }

    @Override
    public void afterFetch(EntityContext entityContext) {
        super.afterFetch(entityContext);
        setStart(true);
        setHlsStreamUrl(getIeeeAddress());
    }

    @Override
    protected void beforePersist() {
        setSnapshotOptions("-an -update 1 -frames:v 1");
    }
}

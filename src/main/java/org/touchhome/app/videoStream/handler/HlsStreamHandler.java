package org.touchhome.app.videoStream.handler;

import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.entity.HlsVideoStreamEntity;
import org.touchhome.bundle.api.EntityContext;

@Log4j2
public class HlsStreamHandler extends RtspStreamHandler {

    public HlsStreamHandler(HlsVideoStreamEntity cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
    }

    @Override
    protected String createRtspUri() {
        return cameraEntity.getIeeeAddress();
    }

    @Override
    protected String getFFMPEGInputOptions() {
        return "";
    }
}

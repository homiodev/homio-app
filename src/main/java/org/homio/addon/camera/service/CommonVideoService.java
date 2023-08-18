package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.ConfigurationException;
import org.homio.addon.camera.entity.CommonVideoStreamEntity;
import org.homio.addon.camera.rtsp.message.sdp.SdpMessage;
import org.homio.addon.camera.scanner.RtspStreamScanner;
import org.homio.api.EntityContext;
import org.homio.api.model.Icon;
import org.homio.api.state.State;
import org.homio.api.state.StringType;

public class CommonVideoService extends BaseVideoService<CommonVideoStreamEntity, CommonVideoService> {

    private VideoSourceType videoSourceType;

    public CommonVideoService(EntityContext entityContext, CommonVideoStreamEntity entity) {
        super(entity, entityContext);
    }

    @Override
    public String getFFMPEGInputOptions(String profile) {
        return videoSourceType.ffmpegInputOptions;
    }

    @Override
    protected boolean pingCamera() {
        return true;
    }

    @Override
    protected void dispose0() {
        // ignore
    }

    @Override
    protected void postInitializeCamera() {
        // ignore
    }

    @Override
    protected void pollCameraConnection() throws ConfigurationException {
        String ieeeAddress = entity.getIeeeAddress();
        if (ieeeAddress == null) {
            throw new ConfigurationException("Url must be not null");
        }

        videoSourceType = VideoSourceType.UNKNOWN;
        if (ieeeAddress.endsWith("m3u8")) {
            videoSourceType = VideoSourceType.HLS;
        } else if (ieeeAddress.startsWith("rtsp://")) {
            videoSourceType = VideoSourceType.RTSP;
        }
    }

    @Override
    public Map<String, State> getAttributes() {
        Map<String, State> map = new HashMap<>(super.getAttributes());
        SdpMessage sdpMessage = RtspStreamScanner.rtspUrlToSdpMessage.get(defaultString(entity.getIeeeAddress(), "\\x0"));
        if (sdpMessage != null) {
            map.put("RTSP Description Message", new StringType(sdpMessage.toString()));
        }
        return map;
    }

    @Override
    protected void updateNotificationBlock() {
        CameraEntrypoint.updateCamera(entityContext, getEntity(),
                null,
                new Icon("fas fa-users-viewfinder", "#669618"),
                null);
    }

    @Override
    protected long getEntityHashCode(CommonVideoStreamEntity entity) {
        return entity.getDeepHashCode();
    }

    @RequiredArgsConstructor
    public enum VideoSourceType {
        HLS(""),
        RTSP("-rtsp_transport tcp -timeout " + TimeUnit.SECONDS.toMicros(10)),
        UNKNOWN("");

        private final String ffmpegInputOptions;
    }
}

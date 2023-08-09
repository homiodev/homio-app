package org.homio.addon.camera.service;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.CommonVideoStreamEntity;
import org.homio.addon.camera.rtsp.message.sdp.SdpMessage;
import org.homio.addon.camera.scanner.RtspStreamScanner;
import org.homio.api.EntityContext;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService;
import org.homio.api.state.State;
import org.homio.api.state.StringType;

public class CommonVideoService extends BaseVideoService<CommonVideoStreamEntity, CommonVideoService> {

    private VideoSourceType videoSourceType;

    public CommonVideoService(EntityContext entityContext, CommonVideoStreamEntity entity) {
        super(entity, entityContext);
    }

    @Override
    public void testVideoOnline() {
        if (getEntity().getIeeeAddress() == null) {
            throw new ServerException("Url must be not null");
        }

        videoSourceType = VideoSourceType.UNKNOWN;
        String ieeeAddress = getEntity().getIeeeAddress();
        if (ieeeAddress != null) {
            if (ieeeAddress.endsWith("m3u8")) {
                videoSourceType = VideoSourceType.HLS;
            } else if (ieeeAddress.startsWith("rtsp://")) {
                videoSourceType = VideoSourceType.RTSP;
            }
        }
    }

    @Override
    public String getRtspUri(String profile) {
        return getEntity().getIeeeAddress();
    }

    @Override
    public String getFFMPEGInputOptions(String profile) {
        return videoSourceType.ffmpegInputOptions;
    }

    @Override
    public Map<String, State> getAttributes() {
        Map<String, State> map = new HashMap<>(super.getAttributes());
        SdpMessage sdpMessage = RtspStreamScanner.rtspUrlToSdpMessage.get(StringUtils.defaultString(getEntity().getIeeeAddress(), "\\x0"));
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
    protected BaseVideoStreamServerHandler createVideoStreamServerHandler() {
        return new RtspCameraStreamHandler(this);
    }

    @Override
    protected void streamServerStarted() {

    }

    @Override
    protected long getEntityHashCode(CommonVideoStreamEntity entity) {
        String ieeeAddress = entity.getIeeeAddress();
        return ieeeAddress == null ? 0 : ieeeAddress.hashCode();
    }

    @RequiredArgsConstructor
    public enum VideoSourceType {
        HLS(""),
        RTSP("-rtsp_transport tcp -timeout " + TimeUnit.SECONDS.toMicros(10)),
        UNKNOWN("");

        private final String ffmpegInputOptions;
    }

    private static class RtspCameraStreamHandler extends BaseVideoStreamServerHandler<CommonVideoService> {

        public RtspCameraStreamHandler(CommonVideoService service) {
            super(service);
        }

        @Override
        protected void handleLastHttpContent(byte[] incomingJpeg) {
        }

        @Override
        protected boolean streamServerReceivedPostHandler(HttpRequest httpRequest) {
            return false;
        }

        @Override
        protected void handlerChildRemoved(ChannelHandlerContext ctx) {
        }
    }
}

package org.touchhome.app.service.video;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.model.entity.CommonVideoStreamEntity;
import org.touchhome.app.service.RtspStreamScanner;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.state.StringType;
import org.touchhome.bundle.api.video.BaseFFMPEGVideoStreamHandler;
import org.touchhome.bundle.api.video.BaseVideoStreamServerHandler;
import org.touchhome.bundle.camera.CameraCoordinator;
import org.touchhome.bundle.camera.rtsp.message.sdp.SdpMessage;
import org.touchhome.common.exception.ServerException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Log4j2
public class CommonUriStreamHandler extends BaseFFMPEGVideoStreamHandler<CommonVideoStreamEntity, CommonUriStreamHandler> {

    private final RtspStreamScanner rtspStreamScanner;
    private VideoSourceType videoSourceType;

    public CommonUriStreamHandler(CommonVideoStreamEntity cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
        rtspStreamScanner = entityContext.getBean(RtspStreamScanner.class);
        detectVideoSourceType();
    }

    @Override
    public void testOnline() {

    }

    @Override
    protected void initialize0() {
        if (videoStreamEntity.getIeeeAddress() == null) {
            throw new ServerException("Url must be not null in entity: " + videoStreamEntity.getTitle());
        }
        super.initialize0();
    }

    @Override
    public void updateVideoStreamEntity(CommonVideoStreamEntity videoStreamEntity) {
        super.updateVideoStreamEntity(videoStreamEntity);
        detectVideoSourceType();
    }

    private void detectVideoSourceType() {
        videoSourceType = VideoSourceType.UNKNOWN;
        if (videoStreamEntity.getIeeeAddress() != null) {
            if (videoStreamEntity.getIeeeAddress().endsWith("m3u8")) {
                videoSourceType = VideoSourceType.HLS;
            } else if (videoStreamEntity.getIeeeAddress().startsWith("rtsp://")) {
                videoSourceType = VideoSourceType.RTSP;
            }
        }
    }

    @Override
    public String getRtspUri(String profile) {
        return videoStreamEntity.getIeeeAddress();
    }

    @Override
    public String getFFMPEGInputOptions(String profile) {
        return videoSourceType.ffmpegInputOptions;
    }

    @Override
    protected BaseVideoStreamServerHandler createVideoStreamServerHandler() {
        return new RtspCameraStreamHandler(this);
    }

    @Override
    protected void streamServerStarted() {

    }

    @Override
    public Map<String, State> getAttributes() {
        Map<String, State> map = new HashMap<>(super.getAttributes());
        SdpMessage sdpMessage = CameraCoordinator.getSdpMessage(videoStreamEntity.getIeeeAddress());
        if (sdpMessage != null) {
            map.put("RTSP Description Message", new StringType(sdpMessage.toString()));
        }
        return map;
    }

    private class RtspCameraStreamHandler extends BaseVideoStreamServerHandler<CommonUriStreamHandler> {

        public RtspCameraStreamHandler(CommonUriStreamHandler rtspStreamHandler) {
            super(rtspStreamHandler);
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

    @RequiredArgsConstructor
    public enum VideoSourceType {
        HLS(""),
        RTSP("-rtsp_transport tcp -stimeout " + TimeUnit.SECONDS.toMicros(10)),
        UNKNOWN("");

        private final String ffmpegInputOptions;

    }
}

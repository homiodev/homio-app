package org.touchhome.app.videoStream.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.entity.BaseFFmpegStreamEntity;
import org.touchhome.app.videoStream.entity.RtspVideoStreamEntity;
import org.touchhome.app.videoStream.rtsp.message.sdp.SdpMessage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.measure.State;
import org.touchhome.bundle.api.measure.StringType;

import java.util.HashMap;
import java.util.Map;

@Log4j2
public class RtspStreamHandler extends BaseFfmpegCameraHandler<BaseFFmpegStreamEntity> {

    public RtspStreamHandler(BaseFFmpegStreamEntity cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
    }

    @Override
    protected void pollingCameraConnection() {
        snapshotIsFfmpeg();
    }

    @Override
    protected String createRtspUri() {
        return cameraEntity.getIeeeAddress();
    }

    @Override
    protected String getFFMPEGInputOptions() {
        return "-rtsp_transport tcp";
    }

    @Override
    protected BaseCameraStreamServerHandler createCameraStreamServerHandler() {
        return new RtspCameraStreamHandler(this);
    }

    @Override
    protected void streamServerStarted() {

    }

    @Override
    protected void bringCameraOnline0() {

    }

    @Override
    public Map<String, State> getAttributes() {
        Map<String, State> map = new HashMap<>(super.getAttributes());
        SdpMessage sdpMessage = cameraCoordinator.getSdpMessage(cameraEntity.getIeeeAddress());
        if (sdpMessage != null) {
            map.put("RTSP Description Message", new StringType(sdpMessage.toString()));
        }
        return map;
    }

    private class RtspCameraStreamHandler extends BaseCameraStreamServerHandler<RtspStreamHandler> {

        public RtspCameraStreamHandler(RtspStreamHandler rtspStreamHandler) {
            super(rtspStreamHandler);
        }

        @Override
        protected void handleLastHttpContent(byte[] incomingJpeg) {
        }

        @Override
        protected boolean handleHttpRequest(QueryStringDecoder queryStringDecoder, ChannelHandlerContext ctx) {
            return false;
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

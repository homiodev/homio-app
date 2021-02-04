package org.touchhome.app.videoStream.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.entity.UsbCameraEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Log4j2
public class UsbCameraHandler extends BaseFfmpegCameraHandler<UsbCameraEntity> {

    public UsbCameraHandler(UsbCameraEntity cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
    }

    @Override
    protected void pollingCameraConnection() {
        snapshotIsFfmpeg();
    }

    @Override
    protected String createRtspUri() {
        return "\"video=" + cameraEntity.getIeeeAddress() + "\"";
    }

    @Override
    protected void bringCameraOnline0() {

    }

    @Override
    protected String getFFMPEGInputOptions() {
        return TouchHomeUtils.OS_NAME.isLinux() ? "-f video4linux2" : "-f dshow";
    }

    @Override
    protected BaseCameraStreamServerHandler createCameraStreamServerHandler() {
        return new UsbCameraStreamHandler(this);
    }

    @Override
    protected void streamServerStarted() {

    }

    private class UsbCameraStreamHandler extends BaseCameraStreamServerHandler<UsbCameraHandler> {

        public UsbCameraStreamHandler(UsbCameraHandler usbCameraHandler) {
            super(usbCameraHandler);
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

package org.homio.addon.camera.service;

import static org.homio.api.EntityContextMedia.FFMPEGFormat.GENERAL;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.UsbCameraEntity;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService;
import org.homio.api.util.CommonUtils;

@Log4j2
public class UsbCameraService extends BaseVideoService<UsbCameraEntity> {

    private final List<String> outputs = new ArrayList<>();
    private FFMPEG ffmpegUsbStream;

    public UsbCameraService(UsbCameraEntity entity, EntityContext entityContext) {
        super(entity, entityContext);
    }

    @Override
    protected long getEntityHashCode(EntityService entity) {
        return getEntity().getDeepHashCode();
    }

    @Override
    public String getRtspUri(String profile) {
        return "udp://@" + outputs.get(0);
    }

    @Override
    public String getFFMPEGInputOptions(String profile) {
        return "";
    }

    @Override
    public void afterInitialize() {
        updateNotificationBlock();
    }

    @Override
    public void afterDispose() {
        updateNotificationBlock();
    }

    public void updateNotificationBlock() {
        CameraEntrypoint.updateCamera(entityContext, getEntity(),
            null,
            new Icon("fas fa-users-viewfinder", "#669618"),
            null);
    }

    @Override
    protected void initialize0() {
        UsbCameraEntity entity = getEntity();
        String url = "video=\"" + entity.getIeeeAddress() + "\"";
        if (StringUtils.isNotEmpty(entity.getAudioSource())) {
            url += ":audio=\"" + entity.getAudioSource() + "\"";
        }
        Set<String> outputParams = new LinkedHashSet<>(entity.getStreamOptions());
        outputParams.add("-f tee");
        outputParams.add("-map 0:v");
        if (StringUtils.isNotEmpty(entity.getAudioSource())) {
            url += ":audio=\"" + entity.getAudioSource() + "\"";
            outputParams.add("-map 0:a");
        }

        outputs.add(CommonUtils.MACHINE_IP_ADDRESS + ":" + entity.getStreamStartPort());
        outputs.add(CommonUtils.MACHINE_IP_ADDRESS + ":" + (entity.getStreamStartPort() + 1));

        ffmpegUsbStream = entityContext.media().buildFFMPEG(getEntityID(), "FFmpeg usb udp re streamer", this, log,
            GENERAL, "-loglevel warning " + (SystemUtils.IS_OS_LINUX ? "-f v4l2" : "-f dshow"), url,
            String.join(" ", outputParams),
            outputs.stream().map(o -> "[f=mpegts]udp://" + o + "?pkt_size=1316").collect(Collectors.joining("|")),
            "", "", null);
        ffmpegUsbStream.startConverting();

        super.initialize0();
    }

    @Override
    protected void dispose0() {
        super.dispose0();
        ffmpegUsbStream.stopConverting();

    }

    @Override
    protected void testVideoOnline() {
        Set<String> aliveVideoDevices = entityContext.media().getVideoDevices();
        if (!aliveVideoDevices.contains(getEntity().getIeeeAddress())) {
            throw new RuntimeException("Camera not available");
        }
    }

    @Override
    protected String createHlsRtspUri() {
        return "udp://@" + outputs.get(1);
    }

    @Override
    protected BaseVideoStreamServerHandler createVideoStreamServerHandler() {
        return new UsbCameraStreamHandler(this);
    }

    @Override
    protected void streamServerStarted() {

    }

    @Override
    protected boolean hasAudioStream() {
        return super.hasAudioStream() || StringUtils.isNotEmpty(getEntity().getAudioSource());
    }

    private static class UsbCameraStreamHandler extends BaseVideoStreamServerHandler<UsbCameraService> {

        public UsbCameraStreamHandler(UsbCameraService usbCameraService) {
            super(usbCameraService);
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

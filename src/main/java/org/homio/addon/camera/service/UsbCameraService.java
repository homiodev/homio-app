package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.api.EntityContextMedia.FFMPEGFormat.MUXER;

import java.awt.Dimension;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.ConfigurationException;
import org.homio.addon.camera.entity.UsbCameraEntity;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.MediaMTXSource;
import org.homio.api.EntityContextMedia.VideoInputDevice;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.homio.app.model.entity.MediaMTXEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Getter
public class UsbCameraService extends BaseVideoService<UsbCameraEntity, UsbCameraService> {

    private FFMPEG ffmpegUsbStream;
    private @Nullable VideoInputDevice input;

    public UsbCameraService(UsbCameraEntity entity, EntityContext entityContext) {
        super(entity, entityContext);
    }

    @Override
    public void ffmpegLog(@NotNull Level level, @NotNull String message) {
        log.log(level, "[{}]: {}", getEntityID(), message);
        bringCameraOnline();
    }

    @Override
    protected void pollCameraConnection() throws Exception {
        Set<String> aliveVideoDevices = entityContext.media().getVideoDevices();
        if (!aliveVideoDevices.contains(entity.getIeeeAddress())) {
            throw new ConfigurationException("W.ERROR.USB_CAMERA_NOT_AVAILABLE");
        }

        // restart if not alive
        ffmpegUsbStream.startConverting();
        keepMjpegRunning();
        // skip taking snapshot
        bringCameraOnline();
    }

    @Override
    protected boolean pingCamera() {
        return ffmpegUsbStream.getIsAlive();
    }

    @Override
    public List<OptionModel> getLogSources() {
        ArrayList<OptionModel> list = new ArrayList<>(super.getLogSources());
        list.add(OptionModel.of("tee", "FFMPEG muxer"));
        return list;
    }

    @Override
    public @Nullable InputStream getSourceLogInputStream(@NotNull String sourceID) {
        if ("tee".equals(sourceID)) {
            return getFFMPEGLogInputStream(ffmpegUsbStream);
        }
        return super.getSourceLogInputStream(sourceID);
    }

    @Override
    public String getHlsUri() {
        return getUdpUrl();
    }

    private @NotNull String getUdpUrl() {
        return "udp://238.0.0.1:%s".formatted(entity.getReStreamUdpPort());
    }

    @Override
    protected void updateNotificationBlock() {
        CameraEntrypoint.updateCamera(entityContext, getEntity(),
                null,
                new Icon("fas fa-users-viewfinder", "#669618"),
                null);
    }

    @Override
    protected void postInitializeCamera() {
        urls.setRtspUri("rtsp://127.0.0.1:%s/%s".formatted(MediaMTXEntity.RTSP_PORT, getEntityID()));
        String ieeeAddress = Objects.requireNonNull(entity.getIeeeAddress());
        input = entityContext.media().createVideoInputDevice(ieeeAddress);
        if (input.getResolutions().length > 0) {
            try {
                entityContext.updateDelayed(entity, e -> {
                    if (entity.getStreamResolutions().isEmpty()) {
                        e.setStreamResolutions(String.join("~~~", input.getResolutionSet()));
                    }
                    if (entity.getHlsLowResolution().isEmpty()) {
                        Dimension dim = Arrays.stream(input.getResolutions())
                                              .min(Comparator.comparingInt(dim2 -> dim2.width * dim2.height)).get();
                        e.setHlsLowResolution(String.format("%dx%d", dim.width, dim.height));
                    }
                    if (entity.getHlsHighResolution().isEmpty()) {
                        Dimension dim = Arrays.stream(input.getResolutions())
                                              .max(Comparator.comparingInt(dim2 -> dim2.width * dim2.height)).get();
                        e.setHlsHighResolution(String.format("%dx%d", dim.width, dim.height));
                    }
                });
            } catch (Exception ex) {
                log.error("[{}]: Error while update usb camera: {}", getEntityID(), CommonUtils.getErrorMessage(ex));
            }
        }

        String url = "video=\"" + ieeeAddress + "\"";
        if (isNotEmpty(entity.getAudioSource())) {
            url += ":audio=\"" + entity.getAudioSource() + "\"";
        }
        Set<String> outputParams = new LinkedHashSet<>();
        outputParams.add("-f tee");
        outputParams.add("-preset ultrafast");
        outputParams.add("-tune zerolatency");
        outputParams.add("-framerate %s".formatted(entity.getStreamFramesPerSecond()));
        outputParams.add("-c:v libx264");
        outputParams.add("-hide_banner");
        outputParams.add("-b:v %sk".formatted(entity.getStreamBitRate()));
        // https://trac.ffmpeg.org/wiki/EncodingForStreamingSites#a-g
        outputParams.add("-g %s".formatted(entity.getStreamFramesPerSecond() * 2));

        if (isNotEmpty(entity.getAudioSource())) {
            url += ":audio=\"" + entity.getAudioSource() + "\"";
            outputParams.add("-c:a mp2");
            outputParams.add("-b:a 128k");
            outputParams.add("-ar 44100");
            outputParams.add("-ac 2");
        }
        outputParams.add("-map 0");

        String output = "";
        output += "[f=mpegts]%s?pkt_size=1316".formatted(getUdpUrl());
        setAttribute("USB-TEE", new StringType(output));

        if(ffmpegUsbStream == null || !ffmpegUsbStream.getIsAlive()) {
            ffmpegUsbStream = entityContext.media().buildFFMPEG(getEntityID(), "FFmpeg usb udp re-streamer", this,
                MUXER, SystemUtils.IS_OS_LINUX ? "-f v4l2" : "-f dshow", url,
                String.join(" ", outputParams),
                output,
                "", "");
            ffmpegUsbStream.startConverting();
        }
        entityContext.media().registerMediaMTXSource(getEntityID(), new MediaMTXSource(getUdpUrl()));
    }

    @Override
    protected void dispose0() {
        FFMPEG.run(ffmpegUsbStream, FFMPEG::stopConverting);
    }

    @Override
    public boolean hasAudioStream() {
        return super.hasAudioStream() || isNotEmpty(entity.getAudioSource());
    }

    @Override
    protected void pollCameraRunnable() {
        FFMPEG.run(ffmpegUsbStream, FFMPEG::restartIfRequire);
        super.pollCameraRunnable();
    }
}

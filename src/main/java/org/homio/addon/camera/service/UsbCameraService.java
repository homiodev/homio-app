package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.api.EntityContextMedia.FFMPEGFormat.GENERAL;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.ConfigurationException;
import org.homio.addon.camera.entity.UsbCameraEntity;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.model.Icon;
import org.homio.api.util.CommonUtils;

@Log4j2
public class UsbCameraService extends BaseVideoService<UsbCameraEntity, UsbCameraService> {

    public final List<String> outputs = new ArrayList<>();
    private FFMPEG ffmpegUsbStream;

    public UsbCameraService(UsbCameraEntity entity, EntityContext entityContext) {
        super(entity, entityContext);
    }

    @Override
    protected void pollCameraConnection() throws Exception {
        Set<String> aliveVideoDevices = entityContext.media().getVideoDevices();
        if (!aliveVideoDevices.contains(entity.getIeeeAddress())) {
            throw new ConfigurationException("Wrong camera configuration");
        }
        super.pollCameraConnection();
    }

    @Override
    protected long getEntityHashCode(UsbCameraEntity entity) {
        return entity.getDeepHashCode();
    }

    @Override
    public String getFFMPEGInputOptions(String profile) {
        return "";
    }

    @Override
    protected boolean pingCamera() {
        return true;
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
        UsbCameraEntity entity = getEntity();
        String url = "video=\"" + entity.getIeeeAddress() + "\"";
        if (isNotEmpty(entity.getAudioSource())) {
            url += ":audio=\"" + entity.getAudioSource() + "\"";
        }
        Set<String> outputParams = new LinkedHashSet<>(entity.getStreamOptions());
        outputParams.add("-f tee");
        outputParams.add("-map 0:v");
        if (isNotEmpty(entity.getAudioSource())) {
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
    }

    @Override
    protected void dispose0() {
        FFMPEG.run(ffmpegUsbStream, FFMPEG::stopConverting);
    }

    @Override
    protected boolean hasAudioStream() {
        return super.hasAudioStream() || isNotEmpty(entity.getAudioSource());
    }
}

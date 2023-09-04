package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;

public interface StreamMJPEG extends HasJsonData {

    String mp4OutOptions = "-c:v copy -c:a copy";

    @UIField(order = 1000, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup(value = "MGPEG_GROUP", borderColor = "#6BB030")
    @UIFieldTab("MJPEG")
    default List<String> getMjpegExtraOptions() {
        return getJsonDataList("mjpeg_eo");
    }

    default void setMjpegExtraOptions(String value) {
        setJsonData("mjpeg_eo", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    @UIFieldSlider(min = 0, max = 10)
    default int getMjpegSnapshotQuality() {
        return getJsonData("mjpeg_q", 5);
    }

    default void setMjpegSnapshotQuality(@Min(0) @Max(10) int value) {
        setJsonData("mjpeg_q", value);
    }

    @UIField(order = 340, hideInView = true)
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    @UIFieldSlider(min = 0.1, max = 4, step = 0.1, header = "frames/sec")
    default double getMjpegFrameRate() {
        return getJsonData("mjpeg_rate", 1);
    }

    default void setMjpegFrameRate(double value) {
        setJsonData("mjpeg_rate", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    default String getMjpegScale() {
        return getJsonData("mjpeg_scale");
    }

    default void setMjpegScale(String value) {
        setJsonData("mjpeg_scale", value);
    }

    @UIField(order = 505, hideInView = true)
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    default String getMjpegInputOptions() {
        return getJsonData("mjpeg_io");
    }

    default void setMjpegInputOptions(String value) {
        setJsonData("mjpeg_io", value);
    }

    default @NotNull <S extends BaseVideoService<T, S>, T extends BaseVideoEntity<T, S>> FFMPEG buildMjpegFFMPEG(
        BaseVideoService<T, S> service) {
        T entity = service.getEntity();
        String rtspUri = service.getUrls().getRtspUri();

        List<String> options = new ArrayList<>();
        options.add("-q:v " + getMjpegSnapshotQuality()); // video quality
        options.add("-r " + getMjpegFrameRate());
        options.add("-update 1");

        if (isNotEmpty(getMjpegScale())) {
            options.add("-vf scale=" + getMjpegScale()); // scale result video
        }
        options.addAll(getMjpegExtraOptions());
        String outOptions = String.join(" ", options);

        return service.getEntityContext().media().buildFFMPEG(
            service.getEntityID(),
            "MJPEG",
            service,
            FFMPEGFormat.MJPEG,
            getMjpegInputOptions() + " -hide_banner",
            rtspUri,
            outOptions,
            entity.getUrl("ipcamera.jpg"),
            entity.getUser(),
            entity.getPassword().asString());
    }

    default <T extends BaseVideoEntity<T, S>, S extends BaseVideoService<T, S>> void recordMp4Async(
        Path filePath, String profile, int secondsToRecord, BaseVideoService<T, S> service) {
        String inputOptions = service.getFFMPEGInputOptions(profile);
        inputOptions = "-y -t " + secondsToRecord + " -hide_banner " + inputOptions;
        FFMPEG ffmpegMP4 = service.getEntityContext()
                                  .media()
                                  .buildFFMPEG(service.getEntityID(), "MP4",
                                      new FFMPEGHandler() {}, FFMPEGFormat.RECORD, inputOptions,
                                      service.getUrls().getRtspUri(profile),
                                      mp4OutOptions, filePath.toString(),
                                      service.getEntity().getUser(), service.getEntity().getPassword().asString());
        FFMPEG.run(ffmpegMP4, FFMPEG::startConverting);
    }
}

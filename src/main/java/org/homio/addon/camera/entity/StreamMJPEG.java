package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @UIField(order = 1, hideInView = true, label = "snapshotQuality")
    @UIFieldGroup(value = "MGPEG_GROUP", order = 1, borderColor = "#B09F3E")
    @UIFieldTab("MJPEG")
    @UIFieldSlider(min = 0, max = 10)
    default int getMjpegSnapshotQuality() {
        return getJsonData("mjpeg_q", 5);
    }

    default void setMjpegSnapshotQuality(@Min(0) @Max(10) int value) {
        setJsonData("mjpeg_q", value);
    }

    @UIField(order = 2, hideInView = true, label = "frameRate")
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    @UIFieldSlider(min = 0.1, max = 4, step = 0.1, header = "frames/sec")
    default double getMjpegFrameRate() {
        return getJsonData("mjpeg_rate", 1);
    }

    default void setMjpegFrameRate(double value) {
        setJsonData("mjpeg_rate", value);
    }

    @UIField(order = 3, hideInView = true, label = "videoScale")
    @UIFieldGroup("MGPEG_GROUP")
    @UIFieldTab("MJPEG")
    default String getMjpegScale() {
        return getJsonData("mjpeg_scale");
    }

    default void setMjpegScale(String value) {
        setJsonData("mjpeg_scale", value);
    }

    @UIField(order = 1, hideInView = true, label = "ffmpegInOptions")
    @UIFieldGroup(value = "ADVANCED", order = 50, borderColor = "#FF1E00")
    @UIFieldTab("MJPEG")
    default String getMjpegInputOptions() {
        return getJsonData("mjpeg_io");
    }

    default void setMjpegInputOptions(String value) {
        setJsonData("mjpeg_io", value);
    }

    @UIField(order = 2, hideInView = true, type = UIFieldType.Chips, label = "ffmpegOutOptions")
    @UIFieldGroup("ADVANCED")
    @UIFieldTab("MJPEG")
    default List<String> getMjpegOutputOptions() {
        return getJsonDataList("mjpeg_oo");
    }

    default void setMjpegOutputOptions(String value) {
        setJsonData("mjpeg_oo", value);
    }

    default @NotNull <S extends BaseVideoService<T, S>, T extends BaseVideoEntity<T, S>> FFMPEG buildMjpegFFMPEG(
        BaseVideoService<T, S> service) {
        T entity = service.getEntity();
        String rtspUri = service.getUrls().getRtspUri();

        String outOptions = getMjpegOutOptions();

        return service.getEntityContext().media().buildFFMPEG(
            service.getEntityID(),
            "MJPEG",
            service,
            FFMPEGFormat.MJPEG,
            getMjpegInputOptions(),
            rtspUri,
            outOptions,
            entity.getUrl("snapshot.jpg"),
            entity.getUser(),
            entity.getPassword().asString());
    }

    @JsonIgnore
    private String getMjpegOutOptions() {
        List<String> options = new ArrayList<>();
        options.add("-q:v " + getMjpegSnapshotQuality()); // video quality
        options.add("-r " + getMjpegFrameRate());
        options.add("-update 1");
        options.add("-hide_banner");

        if (isNotEmpty(getMjpegScale())) {
            options.add("-vf scale=" + getMjpegScale()); // scale result video
        }
        options.addAll(getMjpegOutputOptions());
        return String.join(" ", options);
    }

    default void recordMp4Async(Path filePath, String profile, int secondsToRecord, BaseVideoService<?, ?> service) {
        String inputOptions = "-y -t " + secondsToRecord + " -hide_banner";
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

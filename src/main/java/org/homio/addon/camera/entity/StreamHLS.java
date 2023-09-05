package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.aspectj.util.FileUtil;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StreamHLS extends HasJsonData {

    Pattern RESOLUTION_PATTERN = Pattern.compile("\\d+x\\d+");

    @Nullable String getHlsRtspUriInput();

    @UIField(order = 1, hideInView = true)
    @UIFieldGroup(value = "HLS_GROUP", order = 1, borderColor = "#79D136")
    @UIFieldTab("HLS")
    default int getHlsListSize() {
        return getJsonData("hls_ls", 10);
    }

    default void setHlsListSize(int value) {
        setJsonData("hls_ls", value);
    }

    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    @UIFieldSlider(min = 1, max = 60)
    default int getHlsFileSec() {
        return getJsonData("hls_fs", 2);
    }

    default void setHlsFileSec(int value) {
        setJsonData("hls_fs", value);
    }

    @UIField(order = 3, hideInView = true, label = "videoCodec")
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    default String getHlsVideoCodec() {
        return getJsonData("hls_vc", "libx264");
    }

    default void setHlsVideoCodec(String value) {
        setJsonData("hls_vc", value);
    }

    @UIField(order = 4, hideInView = true, label = "audioCodec")
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    default String getHlsAudioCodec() {
        return getJsonData("hls_ac", "aac");
    }

    default void setHlsAudioCodec(String value) {
        setJsonData("hls_ac", value);
    }

    @UIField(order = 5, hideInView = true, label = "videoScale")
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    default String getHlsScale() {
        return getJsonData("hls_scale");
    }

    default void setHlsScale(String value) {
        setJsonData("hls_scale", value);
    }

    @UIField(order = 1, hideInView = true)
    @UIFieldGroup(value = "HLS_STREAMS", order = 4, borderColor = "#7E80C4")
    @UIFieldTab("HLS")
    default String getHlsLowResolution() {
        return getJsonData("low");
    }

    default void setHlsLowResolution(String value) {
        if (isNotEmpty(value)) {
            value = value.toLowerCase();
            if (!RESOLUTION_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid low resolution value: " + value + ". Pattern: 640x480");
            }
        }
        setJsonData("low", value);
    }

    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("HLS_STREAMS")
    @UIFieldTab("HLS")
    default String getHlsHighResolution() {
        return getJsonData("high");
    }

    default void setHlsHighResolution(String value) {
        if (isNotEmpty(value)) {
            value = value.toLowerCase();
            if (!RESOLUTION_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid high resolution value: " + value + ". Pattern: 640x480");
            }
        }
        setJsonData("high", value);
    }

    @UIField(order = 1, hideInView = true, type = UIFieldType.Chips, label = "ffmpegInOptions")
    @UIFieldGroup(value = "ADVANCED", order = 50, borderColor = "#FF1E00")
    @UIFieldTab("HLS")
    default List<String> getHlsInputOptions() {
        return getJsonDataList("hls_io");
    }

    default void setHlsInputOptions(String value) {
        setJsonData("hls_io", value);
    }

    @UIField(order = 2, hideInView = true, type = UIFieldType.Chips, label = "ffmpegOutOptions")
    @UIFieldGroup("ADVANCED")
    @UIFieldTab("HLS")
    default List<String> getHlsOutputOptions() {
        return getJsonDataList("hls_oo");
    }

    default void setHlsOutputOptions(String value) {
        setJsonData("hls_oo", value);
    }

    @SneakyThrows
    default @NotNull FFMPEG buildHlsFFMPEG(@NotNull Resolution resolution, BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service) {
        Path directory = service.getFfmpegHLSOutputPath();
        String input = getHlsRtspUriInput();
        if (input == null) {
            input = service.getUrls().getRtspUri(resolution.toString()); // todo: not works
        }
        String streamPrefix = "stream_%s".formatted(resolution);
        return service.getEntityContext().media().buildFFMPEG(
                          service.getEntityID() + "_" + resolution,
                          "HLS[%s]".formatted(resolution),
                          service, FFMPEGFormat.HLS,
                          String.join(" ", getHlsInputOptions()),
                          input,
                          buildHlsOptions(service, resolution), directory.resolve("%s.m3u8".formatted(streamPrefix)).toString(),
                          service.getEntity().getUser(), service.getEntity().getPassword().asString())
                      .addDestroyListener("del-old-segments", () -> {
                          // clean all files on destroy
                          FileUtil.deleteContents(directory.toFile(), pathname -> pathname.getName().startsWith(streamPrefix), false);
                      });
    }

    default String buildHlsOptions(@NotNull BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service,
        @NotNull Resolution resolution) {
        List<String> options = new ArrayList<>();
        // To force a keyframe every 2 seconds you can specify the GOP size using -g:
        // Where 29.97 fps * 2s ~= 60 frames, meaning a keyframe each 60 frames.
        options.add("-g %s".formatted(30 * getHlsFileSec()));
        options.add("-c:v " + getHlsVideoCodec()); // video codec
        options.add("-hls_flags delete_segments"); // remove old segments
        options.add("-hls_init_time 1"); // build first ts ASAP
        options.add("-hls_time " + getHlsFileSec()); // ~ 2sec per file ?
        options.add("-hls_list_size " + getHlsListSize()); // how many files
        options.add("-preset ultrafast");
        options.add("-tune zerolatency");
        options.add("-hide_banner");
        if (resolution != Resolution.def) {
            options.add("-s " + (resolution == Resolution.low ? service.getEntity().getHlsLowResolution() : service.getEntity().getHlsHighResolution()));
        }

        if (isNotEmpty(getHlsScale())) {
            options.add("-vf scale=" + getHlsScale()); // scale result video
        }
        if (service.hasAudioStream()) {
            options.add("-c:a " + getHlsAudioCodec());
            options.add("-ac 2"); // audio channels (stereo)
            options.add("-ab 32k"); // audio bitrate in Kb/s
            options.add("-ar 44100"); // audio sampling rate
        }
        options.addAll(getHlsOutputOptions());
        return String.join(" ", options);
    }

    enum Resolution {
        low, high, def
    }
}

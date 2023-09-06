package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

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

public interface StreamDASH extends HasJsonData {

    Pattern RESOLUTION_PATTERN = Pattern.compile("\\d+x\\d+");

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

    @UIField(order = 4, hideInView = true, label = "bitrate")
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    @UIFieldSlider(min = 512, max = 4000)
    default int getHlsBitRate() {
        return getJsonData("hls_vc", 2000);
    }

    default void setHlsBitRate(int value) {
        setJsonData("hls_vc", value);
    }

    @UIField(order = 5, hideInView = true, label = "audioCodec")
    @UIFieldGroup("HLS_GROUP")
    @UIFieldTab("HLS")
    default String getHlsAudioCodec() {
        return getJsonData("hls_ac", "aac");
    }

    default void setHlsAudioCodec(String value) {
        setJsonData("hls_ac", value);
    }

    @UIField(order = 6, hideInView = true, label = "videoScale")
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
    default @NotNull FFMPEG buildDashFfmpeg(BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service) {
        String input = service.getHlsUri();
        if (input == null) {
            throw new IllegalArgumentException("Unable to find video service hls url");
        }
        String streamPrefix = "%s_dash".formatted(service.getEntity());
        return service.getEntityContext().media().buildFFMPEG(
                          service.getEntityID(),
                          "DASH",
                          service,
                          FFMPEGFormat.DASH,
                          "-re " + String.join(" ", getHlsInputOptions()),
                          input,
                          buildDashOptions(service, streamPrefix),
                          "%s.mpd".formatted(streamPrefix),
                          service.getEntity().getUser(),
                          service.getEntity().getPassword().asString())
                      .setWorkingDirectory(BaseVideoService.SHARE_DIR)
                      .addDestroyListener("DOS", () -> {
                          FileUtil.deleteContents(BaseVideoService.SHARE_DIR.toFile(),
                              file -> file.getName().startsWith(streamPrefix), false);
                      });
    }

    default String buildDashOptions(@NotNull BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service, String streamPrefix) {
        List<String> options = new ArrayList<>();
        options.add("-c:v libx264");
        options.add("-b:v %sk" + getHlsBitRate());
        options.add("-use_template 1");
        options.add("-use_timeline 1");
        options.add("-window_size 10");
        options.add("-extra_window_size 5");
        options.add("-init_seg_name " + streamPrefix + "_init-stream$RepresentationID$.$ext$");
        options.add("-media_seg_name " + streamPrefix + "_chunk-stream$RepresentationID$-$Number%05d$.$ext$");
        options.add("-f dash");
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
}

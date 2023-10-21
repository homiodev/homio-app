package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.aspectj.util.FileUtil;
import org.homio.addon.camera.service.BaseCameraService;
import org.homio.api.ContextMedia.FFMPEG;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;

public interface StreamDASH extends HasJsonData {

    Pattern RESOLUTION_PATTERN = Pattern.compile("\\d+x\\d+");

    @UIField(order = 3, hideInView = true, label = "videoCodec")
    @UIFieldGroup("DASH_GROUP")
    @UIFieldTab("DASH")
    default String getDashVideoCodec() {
        return getJsonData("dash_vc", "libx264");
    }

    default void setDashVideoCodec(String value) {
        setJsonData("dash_vc", value);
    }

    @UIField(order = 4, hideInView = true, label = "bitRate")
    @UIFieldGroup("DASH_GROUP")
    @UIFieldTab("DASH")
    @UIFieldSlider(min = 512, max = 4000, header = "kbs")
    default int getDashBitRate() {
        return getJsonData("dash_vc", 2000);
    }

    default void setDashBitRate(int value) {
        setJsonData("dash_vc", value);
    }

    @UIField(order = 5, hideInView = true, label = "audioCodec")
    @UIFieldGroup("DASH_GROUP")
    @UIFieldTab("DASH")
    default String getDashAudioCodec() {
        return getJsonData("dash_ac", "aac");
    }

    default void setDashAudioCodec(String value) {
        setJsonData("dash_ac", value);
    }

    @UIField(order = 6, hideInView = true, label = "videoScale")
    @UIFieldGroup("DASH_GROUP")
    @UIFieldTab("DASH")
    default String getDashScale() {
        return getJsonData("dash_scale");
    }

    default void setDashScale(String value) {
        setJsonData("dash_scale", value);
    }

    @UIField(order = 1, hideInView = true, type = UIFieldType.Chips, label = "ffmpegInOptions")
    @UIFieldGroup(value = "ADVANCED", order = 50, borderColor = "#FF1E00")
    @UIFieldTab("DASH")
    default List<String> getDashInputOptions() {
        return getJsonDataList("dash_io");
    }

    default void setDashInputOptions(String value) {
        setJsonData("dash_io", value);
    }

    @UIField(order = 2, hideInView = true, type = UIFieldType.Chips, label = "ffmpegOutOptions")
    @UIFieldGroup("ADVANCED")
    @UIFieldTab("DASH")
    default List<String> getDashOutputOptions() {
        return getJsonDataList("dash_oo");
    }

    default void setDashOutputOptions(String value) {
        setJsonData("dash_oo", value);
    }

    @SneakyThrows
    default @NotNull FFMPEG buildDashFfmpeg(BaseCameraService<? extends BaseCameraEntity<?, ?>, ?> service) {
        String input = service.getDashUri();
        if (input == null) {
            throw new IllegalArgumentException("Unable to find video service dash url");
        }
        String streamPrefix = "%s_dash".formatted(service.getEntityID());
        return service.context().media().buildFFMPEG(
                          service.getEntityID(),
                          "DASH",
                          service,
                          FFMPEGFormat.DASH,
                          "-re " + String.join(" ", getDashInputOptions()),
                          input,
                          buildDashOptions(service, streamPrefix),
                          "%s.mpd".formatted(streamPrefix),
                          service.getEntity().getUser(),
                          service.getEntity().getPassword().asString())
                      .setWorkingDirectory(BaseCameraService.SHARE_DIR)
                      .addDestroyListener("DOS", () -> {
                          FileUtil.deleteContents(BaseCameraService.SHARE_DIR.toFile(),
                              file -> file.getName().startsWith(streamPrefix), false);
                      });
    }

    default String buildDashOptions(@NotNull BaseCameraService<? extends BaseCameraEntity<?, ?>, ?> service, String streamPrefix) {
        List<String> options = new ArrayList<>();
        options.add("-c:v %s".formatted(getDashVideoCodec()));
        options.add("-b:v %sk".formatted(getDashBitRate()));
        options.add("-use_template 1");
        options.add("-use_timeline 1");
        options.add("-window_size 10");
        options.add("-extra_window_size 5");
        options.add("-init_seg_name " + streamPrefix + "_init-stream$RepresentationID$.$ext$");
        options.add("-media_seg_name " + streamPrefix + "_chunk-stream$RepresentationID$-$Number%05d$.$ext$");
        options.add("-f dash");
        if (isNotEmpty(getDashScale())) {
            options.add("-vf scale=%s".formatted(getDashScale())); // scale result video
        }
        if (service.hasAudioStream()) {
            options.add("-c:a %s".formatted(getDashAudioCodec()));
            options.add("-ac 2"); // audio channels (stereo)
            options.add("-ab 32k"); // audio bitrate in Kb/s
            options.add("-ar 44100"); // audio sampling rate
        }
        options.addAll(getDashOutputOptions());
        return String.join(" ", options);
    }
}

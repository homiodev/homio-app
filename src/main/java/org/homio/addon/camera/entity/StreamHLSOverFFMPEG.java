package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContextMedia;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StreamHLSOverFFMPEG extends HasJsonData {

    @Nullable String getHlsRtspUriInput();

    @UIField(order = 1000, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup("HLS_GROUP")
    default List<String> getExtraOptions() {
        return getJsonDataList("extraOpts");
    }

    default void setExtraOptions(String value) {
        setJsonData("extraOpts", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default int getHlsListSize() {
        return getJsonData("hlsListSize", 10);
    }

    default void setHlsListSize(int value) {
        setJsonData("hlsListSize", value);
    }

    @UIField(order = 340, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    @UIFieldSlider(min = 1, max = 60)
    default int getHlsFileSec() {
        return getJsonData("hlsFileSec", 2);
    }

    default void setHlsFileSec(int value) {
        setJsonData("hlsFileSec", value);
    }

    @UIField(order = 400, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getVideoCodec() {
        return getJsonData("vcodec", "copy");
    }

    default void setVideoCodec(String value) {
        setJsonData("vcodec", value);
    }

    @UIField(order = 410, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getAudioCodec() {
        return getJsonData("acodec", "aac");
    }

    default void setAudioCodec(String value) {
        setJsonData("acodec", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getHlsScale() {
        return getJsonData("hls_scale");
    }

    default void setHlsScale(String value) {
        setJsonData("hls_scale", value);
    }

    default @NotNull FFMPEG buildHlsFFMPEG(@NotNull String profileOrResolution, BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service) {
        Path directory = service.getFfmpegHLSOutputPath().resolve(profileOrResolution);
        String input = getHlsRtspUriInput();
        if (input == null) {
            input = service.getUrls().getRtspUri(profileOrResolution);
        }
        EntityContextMedia.FFMPEG ffmpegHLS =
            service.getEntityContext().media().buildFFMPEG(service.getEntityID() + "-" + profileOrResolution, "FFMPEG HLS [%s]".formatted(profileOrResolution),
                service, FFMPEGFormat.HLS,
                "-hide_banner " + service.getFFMPEGInputOptions(),
                input,
                buildHlsOptions(service), directory.resolve("ipcamera.m3u8").toString(),
                service.getEntity().getUser(), service.getEntity().getPassword().asString());
        ffmpegHLS.getMetadata().put("output", directory.toString());
        ffmpegHLS.setDestroyListener(() -> {
            try {
                FileUtils.deleteDirectory(directory.toFile());
            } catch (IOException e) {
                service.getLog().warn("Unable to delete hls directory: {}", directory);
            }
        });
        return ffmpegHLS;
    }

    default String buildHlsOptions(BaseVideoService<? extends BaseVideoEntity<?, ?>, ?> service) {
        List<String> options = new ArrayList<>();
        // To force a keyframe every 2 seconds you can specify the GOP size using -g:
        // Where 29.97 fps * 2s ~= 60 frames, meaning a keyframe each 60 frames.
        options.add("-g %s".formatted(30 * getHlsFileSec()));
        options.add("-c:v " + getVideoCodec()); // video codec
        options.add("-hls_flags delete_segments"); // remove old segments
        options.add("-hls_init_time 1"); // build first ts ASAP
        options.add("-hls_time " + getHlsFileSec()); // ~ 2sec per file ?
        options.add("-hls_list_size " + getHlsListSize()); // how many files
        options.add("-hls_segment_filename output%03d.ts");
        if (isNotEmpty(getHlsScale())) {
            options.add("-vf scale=" + getHlsScale()); // scale result video
        }
        if (service.hasAudioStream()) {
            options.add("-c:a " + getAudioCodec());
            options.add("-ac 2"); // audio channels (stereo)
            options.add("-ab 32k"); // audio bitrate in Kb/s
            options.add("-ar 44100"); // audio sampling rate
        }
        options.addAll(getExtraOptions());
        return String.join(" ", options);
    }
}

package org.homio.addon.camera.entity.storage;

import static org.homio.api.EntityContextMedia.FFMPEGFormat.RECORD;

import jakarta.persistence.Entity;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.service.BaseCameraService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Entity
@UISidebarChildren(icon = "app/client/assets/items/camera/loop-record.png", color = "#0088CC")
public class FFMPEGLoopRecordStorageEntity extends VideoBaseStorageService<FFMPEGLoopRecordStorageEntity> {

    private static Map<String, FFMPEG> ffmpegServices = new HashMap<>();

    @UIField(order = 11)
    public int getSegmentTime() {
        return getJsonData("st", 300);
    }

    public void setSegmentTime(int value) {
        setJsonData("st", value);
    }

    @UIField(order = 20)
    public int getMaxSegments() {
        return getJsonData("ms", 10);
    }

    public void setMaxSegments(int maxClips) {
        setJsonData("ms", maxClips);
    }

    @UIField(order = 30)
    public MuxerType getMuxerType() {
        return getJsonDataEnum("mt", MuxerType.segments);
    }

    public FFMPEGLoopRecordStorageEntity setMuxerType(MuxerType value) {
        setJsonDataEnum("mt", value);
        return this;
    }

    /**
     * Advanced settings
     */
    @UIField(order = 400, hideInView = true)
    public String getVideoCodec() {
        return getJsonData("vc", "copy");
    }

    public FFMPEGLoopRecordStorageEntity setVideoCodec(String value) {
        setJsonData("vc", value);
        return this;
    }

    @UIField(order = 401, hideInView = true)
    public String getAudioCodec() {
        return getJsonData("ac", "copy");
    }

    public FFMPEGLoopRecordStorageEntity setAudioCodec(String value) {
        setJsonData("ac", value);
        return this;
    }

    @UIField(order = 402, hideInView = true)
    public String getSelectStream() {
        return getJsonData("map", "0");
    }

    public FFMPEGLoopRecordStorageEntity setSelectStream(String value) {
        setJsonData("map", value);
        return this;
    }

    @UIField(order = 1000, hideInView = true, type = UIFieldType.Chips)
    public List<String> getExtraOptions() {
        return getJsonDataList("eo");
    }

    public void setExtraOptions(String value) {
        setJsonData("eo", value);
    }

    @UIField(order = 402, hideInView = true)
    public boolean getVerbose() {
        return getJsonData("vb", false);
    }

    public FFMPEGLoopRecordStorageEntity setVerbose(boolean value) {
        setJsonData("vb", value);
        return this;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ffmpeglr";
    }

    @Override
    public String getDefaultName() {
        return "FFMPEG FS loop storage (" + getMuxerType() + ")";
    }

    @Override
    public @Nullable Icon getEntityIcon() {
        return new Icon("fas fa-arrows-spin", "#0088CC");
    }

    @SneakyThrows
    @Override
    public void startRecord(String id, String output, String profile, DeviceBaseEntity deviceEntity, EntityContext entityContext) {
        stopRecord(id, output, deviceEntity);
        if (!(deviceEntity instanceof BaseCameraEntity)) {
            throw new IllegalArgumentException("Unable to start video record for non ffmpeg compatible source");
        }

        if (getMuxerType() == MuxerType.hls && !output.endsWith(".m3u8")) {
            throw new IllegalArgumentException("To record to hls output need set file extension as .m3u8");
        }

        BaseCameraEntity videoStreamEntity = (BaseCameraEntity) deviceEntity;
        BaseCameraService service = videoStreamEntity.getService();

        FFMPEGHandler ffmpegHandler = new FFMPEGHandler() {

            @Override
            public void ffmpegError(@NotNull String error) {
                log.error("[{}]: Record error: <{}>", deviceEntity.getEntityID(), error);
            }

            @Override
            public void ffmpegLog(@NotNull Level level, @NotNull String message) {
                log.log(level, "[{}]: {}", deviceEntity.getEntityID(), message);
            }
        };
        String target = buildOutput(output);
        Path path = Paths.get(target);
        if (!path.isAbsolute()) {
            path = CommonUtils.getMediaPath().resolve(BaseCameraEntity.FOLDER + "_" + profile)
                    .resolve("ffmpeg").resolve(target);
        }
        Path folder = path.getParent();
        CommonUtils.createDirectoriesIfNotExists(folder);

        String source = service.urls.getSnapshotUri(profile);
        log.info("[{}]: Start ffmpeg video recording from source: <{}> to: <{}>", getEntityID(), source, path);
        FFMPEG ffmpeg = entityContext.media().buildFFMPEG(getEntityID(), "FFMPEG loop record", ffmpegHandler,
                RECORD, getVerbose() ? "" : "-hide_banner", source,
                buildFFMPEGRecordCommand(folder), path.toString(),
                videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString());
        ffmpegServices.put(id, ffmpeg);
        ffmpeg.startConverting();
    }

    @Override
    public void stopRecord(String id, String output, DeviceBaseEntity cameraEntity) {
        FFMPEG ffmpeg = ffmpegServices.remove(id);
        if (ffmpeg != null) {
            ffmpeg.stopConverting();
        }
    }

    public String buildFFMPEGRecordCommand(Path folder) {
        List<String> options = new ArrayList<>();
        options.add("-vcodec " + getVideoCodec());
        options.add("-acodec " + getAudioCodec());
        options.add("-map " + getSelectStream());
        options.addAll(getExtraOptions());

        if (getMuxerType() == MuxerType.hls) {
            options.add("-f hls");
            options.add("-hls_time " + getSegmentTime());
            options.add("-hls_list_size " + getMaxSegments());
            options.add("-hls_flags delete_segments");
            options.add("-reset_timestamps 1");
        } else {
            options.add("-f segment");
            options.add("-segment_time " + getSegmentTime());
            options.add("-write_empty_segments 1");
            options.add("-segment_list_size " + getMaxSegments());
            options.add("-segment_wrap " + getMaxSegments());
            options.add("-segment_format mp4");

            options.add("-segment_list_type m3u8");
            options.add("-segment_list " + folder.resolve("playlist.m3u8"));
        }

        return String.join(" ", options);
    }

    private String buildOutput(String output) {
        if (getMuxerType() == MuxerType.hls) {
            return output.endsWith(".m3u8") ? output : output + ".m3u8";
        }
        return output.contains("-%03d.mp4") ? output : output + "-%03d.mp4";
    }

    public enum MuxerType {
        segments, hls
    }
}

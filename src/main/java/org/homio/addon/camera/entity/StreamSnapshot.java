package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import org.homio.addon.camera.service.BaseCameraService;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldType;

public interface StreamSnapshot extends HasJsonData {

    @UIField(order = 1, hideInView = true)
    @UIFieldGroup(value = "SNAPSHOT_GROUP", order = 1, borderColor = "#79D136")
    @UIFieldTab("SNAPSHOT")
    @UIFieldSlider(min = 0, max = 10)
    default int getSnapshotQuality() {
        return getJsonData("snap_q", 5);
    }

    default void setSnapshotQuality(@Min(0) @Max(10) int value) {
        setJsonData("snap_q", value);
    }

    @UIField(order = 2, hideInView = true, label = "videoScale")
    @UIFieldGroup("SNAPSHOT_GROUP")
    @UIFieldTab("SNAPSHOT")
    default String getSnapshotScale() {
        return getJsonData("snap_scale");
    }

    default void setSnapshotScale(String value) {
        setJsonData("snap_scale", value);
    }

    @UIField(order = 1, hideInView = true, label = "ffmpegInOptions")
    @UIFieldGroup(value = "ADVANCED", order = 50, borderColor = "#FF1E00")
    @UIFieldTab("SNAPSHOT")
    default String getSnapshotInputOptions() {
        return getJsonData("snap_io");
    }

    default void setSnapshotInputOptions(String value) {
        setJsonData("snap_io", value);
    }

    @UIField(order = 2, hideInView = true, type = UIFieldType.Chips, label = "ffmpegOutOptions")
    @UIFieldGroup("ADVANCED")
    @UIFieldTab("SNAPSHOT")
    default List<String> getSnapshotOutputOptions() {
        return getJsonDataList("snap_eo");
    }

    default void setSnapshotOutputOptions(String value) {
        setJsonData("snap_eo", value);
    }

    default <T extends BaseCameraEntity<T, S>, S extends BaseCameraService<T, S>> FFMPEG buildSnapshotFFMPEG(
        BaseCameraService<T, S> service) {
        T entity = service.getEntity();
        String rtspUri = service.getUrls().getRtspUri();

        return service.getEntityContext().media().buildFFMPEG(
            service.getEntityID(),
            "SNAPSHOT",
            new FFMPEGHandler(){},
            FFMPEGFormat.SNAPSHOT,
            getSnapshotInputOptions(),
            rtspUri,
            getSnapshotOutOptions(),
            entity.getUrl("snapshot.jpg"),
            entity.getUser(),
            entity.getPassword().asString());
    }

    @JsonIgnore
    default String getSnapshotOutOptions() {
        List<String> options = new ArrayList<>();
        options.add("-q:v " + getSnapshotQuality());
        options.add("-threads 1");
        options.add("-frames:v 1");
        options.add("-skip_frame nokey");
        options.add("-hide_banner");
        options.add("-an");

        if (isNotEmpty(getSnapshotScale())) {
            options.add("-vf scale=" + getSnapshotScale());
        }
        options.addAll(getSnapshotOutputOptions());
        return String.join(" ", options);
    }
}

package org.homio.addon.camera.entity;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

public interface StreamSnapshot extends HasJsonData {

    @UIField(order = 1000, hideInView = true, type = UIFieldType.Chips)
    @UIFieldGroup(value = "SNAPSHOT_GROUP", borderColor = "#79D136")
    @UIFieldTab("SNAPSHOT")
    default List<String> getSnapshotExtraOptions() {
        return getJsonDataList("snap_eo");
    }

    default void setSnapshotExtraOptions(String value) {
        setJsonData("snap_eo", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("SNAPSHOT_GROUP")
    @UIFieldTab("SNAPSHOT")
    @UIFieldSlider(min = 0, max = 10)
    default int getSnapshotQuality() {
        return getJsonData("snap_q", 5);
    }

    default void setSnapshotQuality(@Min(0) @Max(10) int value) {
        setJsonData("snap_q", value);
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("SNAPSHOT_GROUP")
    @UIFieldTab("SNAPSHOT")
    default String getSnapshotScale() {
        return getJsonData("snap_scale");
    }

    default void setSnapshotScale(String value) {
        setJsonData("snap_scale", value);
    }

    @UIField(order = 505, hideInView = true)
    @UIFieldGroup("SNAPSHOT_GROUP")
    @UIFieldTab("SNAPSHOT")
    default String getSnapshotExtraInputOptions() {
        return getJsonData("snap_io");
    }

    default void setSnapshotExtraInputOptions(String value) {
        setJsonData("snap_io", value);
    }

    default <T extends BaseVideoEntity<T, S>, S extends BaseVideoService<T, S>> FFMPEG buildSnapshotFFMPEG(
        BaseVideoService<T, S> service) {
        T entity = service.getEntity();
        String rtspUri = service.getUrls().getRtspUri();

        return service.getEntityContext().media().buildFFMPEG(
            service.getEntityID(),
            "SNAPSHOT",
            new FFMPEGHandler(){},
            FFMPEGFormat.SNAPSHOT,
            getSnapshotExtraInputOptions(),
            rtspUri,
            getSnapshotOutputOptions(service),
            entity.getUrl("snapshot.jpg"),
            entity.getUser(),
            entity.getPassword().asString());
    }

    default <T extends BaseVideoEntity<T, S>, S extends BaseVideoService<T, S>> String getSnapshotOutputOptions(
        BaseVideoService<T, S> service) {
        List<String> options = new ArrayList<>();
        options.add(service.getFFMPEGInputOptions());
        options.add("-q:v " + getSnapshotQuality());
        options.add("-threads 1");
        options.add("-frames:v 1");
        options.add("-skip_frame nokey");
        options.add("-hide_banner");
        options.add("-an");

        if (isNotEmpty(getSnapshotScale())) {
            options.add("-vf scale=" + getSnapshotScale());
        }
        options.addAll(getSnapshotExtraOptions());
        return String.join(" ", options);
    }
}

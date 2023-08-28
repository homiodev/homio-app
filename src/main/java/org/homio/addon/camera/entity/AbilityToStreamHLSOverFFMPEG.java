package org.homio.addon.camera.entity;

import java.util.List;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.Nullable;

public interface AbilityToStreamHLSOverFFMPEG<T> extends HasJsonData {

    @Nullable String getHlsRtspUri();

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
}

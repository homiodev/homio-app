package org.homio.addon.camera.entity;

import java.util.List;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
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
        return getJsonData("hlsListSize", 5);
    }

    default T setHlsListSize(int value) {
        setJsonData("hlsListSize", value);
        return (T) this;
    }

    @UIField(order = 400, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getVideoCodec() {
        return getJsonData("vcodec", "copy");
    }

    default T setVideoCodec(String value) {
        setJsonData("vcodec", value);
        return (T) this;
    }

    @UIField(order = 410, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getAudioCodec() {
        return getJsonData("acodec", "aac");
    }

    default T setAudioCodec(String value) {
        setJsonData("acodec", value);
        return (T) this;
    }

    @UIField(order = 320, hideInView = true)
    @UIFieldGroup("HLS_GROUP")
    default String getHlsScale() {
        return getJsonData("hls_scale");
    }

    default T setHlsScale(String value) {
        setJsonData("hls_scale", value);
        return (T) this;
    }
}

package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public interface WidgetVideoSourceResolver {

    Pattern VIDEO_FORMATS = Pattern.compile("webrtc|webm|ogv|flv|avi|mp4|ts|m3u8|mjpeg");
    Pattern IMAGE_FORMATS = Pattern.compile("jpg|png|gif");

    default VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        return resolveDataSource(item.getValueDataSource());
    }

    VideoEntityResponse resolveDataSource(String valueDataSource);

    @Getter
    @RequiredArgsConstructor
    class VideoEntityResponse {

        private final @NotNull String valueDataSource;
        private final @NotNull String dataSource;
        private final @NotNull String source;
        private final @NotNull String type;
        private @Nullable
        @Setter
        @Accessors(chain = true) String error;
        private final @NotNull List<String> resolutions = new ArrayList<>();
        private @Nullable
        @Setter Collection<UIInputEntity> actions;
        private @Nullable
        @Setter String poster;
    }
}

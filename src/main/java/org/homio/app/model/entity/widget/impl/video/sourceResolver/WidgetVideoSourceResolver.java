package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WidgetVideoSourceResolver {

    VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item);

    @Getter
    @RequiredArgsConstructor
    class VideoEntityResponse {

        private final @NotNull String valueDataSource;
        private final @NotNull String dataSource;
        private final @NotNull String source;
        private final @NotNull String type;
        private @Nullable @Setter @Accessors(chain = true) String error;
        private final @NotNull List<String> resolutions = new ArrayList<>();
        private @Nullable @Setter Collection<UIInputEntity> actions;
        private @Nullable @Setter String poster;

        public static @NotNull String getVideoType(String url) {
            if (url.startsWith("https://youtu")) {
                return "video/youtube";
            }
            if (url.startsWith("https://vimeo")) {
                return "video/vimeo";
            }
            if (url.endsWith(".mp4")) {
                return "video/mp4";
            }
            if (url.endsWith(".jpg")) {
                return "image/jpg";
            }
            if (url.endsWith(".mjpeg")) {
                return "video/mjpeg";
            }
            if (url.endsWith(".ts")) {
                return "video/MP2T";
            }
            if (url.endsWith(".webm")) {
                return "video/webm";
            }
            if (url.endsWith(".ogv")) {
                return "video/ogg";
            }
            if (url.endsWith(".gif")) {
                return "image/gif";
            }
            if (url.endsWith(".m3u8")) {
                return "application/x-mpegURL";
            }
            if (url.endsWith(".mpd")) {
                return "application/dash+xml";
            }
            if (url.endsWith(".webrtc")) {
                return "video/webrtc";
            }
            return "video/unknown";
        }
    }
}

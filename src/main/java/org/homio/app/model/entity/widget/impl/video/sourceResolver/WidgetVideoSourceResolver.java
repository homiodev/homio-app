package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WidgetVideoSourceResolver {

    VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item);

    @Getter
    class VideoEntityResponse {

        private final String dataSource;
        private final String source;
        private final String type;
        private final List<String> resolutions = new ArrayList<>();
        @Setter
        private Collection<UIInputEntity> actions;

        public VideoEntityResponse(@NotNull String dataSource, @NotNull String source) {
            this.dataSource = dataSource;
            this.source = source;
            this.type = getVideoType(source);
        }

        public static String getVideoType(String url) {
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
            if (url.endsWith(".ts")) {
                return "video/MP2T";
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
            return "video";
        }
    }
}

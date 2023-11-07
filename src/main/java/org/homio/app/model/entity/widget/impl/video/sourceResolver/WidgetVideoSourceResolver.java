package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            if (url.endsWith(".ts")) {
                return "video/MP2T";
            }
            if (url.endsWith(".ogv")) {
                return "video/ogg";
            }
            if (url.endsWith(".m3u8")) {
                return "application/x-mpegURL";
            }
            if (url.endsWith(".mpd")) {
                return "application/dash+xml";
            }
            String extension = StringUtils.defaultString(FilenameUtils.getExtension(url));
            if (FSWidgetVideoSourceResolver.IMAGE_FORMATS.matcher(extension).matches()) {
                return "image/" + extension;
            }
            if (FSWidgetVideoSourceResolver.VIDEO_FORMATS.matcher(extension).matches()) {
                return "video/" + extension;
            }
            return "video/unknown";
        }
    }
}

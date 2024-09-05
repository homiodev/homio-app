package org.homio.app.utils;

import org.apache.commons.io.FilenameUtils;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.FSWidgetVideoSourceResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MediaUtils {
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
        String extension = Objects.toString(FilenameUtils.getExtension(url), "");
        if (FSWidgetVideoSourceResolver.IMAGE_FORMATS.matcher(extension).matches()) {
            return "image/" + extension;
        }
        if (FSWidgetVideoSourceResolver.VIDEO_FORMATS.matcher(extension).matches()) {
            return "video/" + extension;
        }
        return "video/unknown";
    }
}

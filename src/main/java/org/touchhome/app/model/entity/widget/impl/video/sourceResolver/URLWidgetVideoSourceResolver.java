package org.touchhome.app.model.entity.widget.impl.video.sourceResolver;

import org.springframework.stereotype.Component;
import org.touchhome.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.touchhome.app.rest.MediaController;

import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {
    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String videoType = getVideoType(item.getDataSource());
        try {
            if (Files.exists(Paths.get(item.getDataSource()))) {
                return new VideoEntityResponse(item.getDataSource(), MediaController.createVideoLink(item.getDataSource()),
                        videoType);
            }
        } catch (Exception ignore) {}
        if (item.getDataSource().startsWith("http")) {
            return new VideoEntityResponse(item.getDataSource(), item.getDataSource(), videoType);
        }
        return null;
    }

    private String getVideoType(String url) {
        if (url.startsWith("https://youtu")) {
            return "video/youtube";
        }
        if (url.startsWith("https://vimeo")) {
            return "video/vimeo";
        }
        if (url.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "video";
    }
}

package org.touchhome.app.model.entity.widget.impl.video.sourceResolver;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;
import org.touchhome.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.touchhome.app.rest.MediaController;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String videoType = getVideoType(item.getValueDataSource());
        try {
            if (Files.exists(Paths.get(item.getValueDataSource()))) {
                return new VideoEntityResponse(
                        item.getValueDataSource(),
                        MediaController.createVideoLink(item.getValueDataSource()),
                        videoType);
            }
        } catch (Exception ignore) {
        }
        if (item.getValueDataSource().startsWith("http")) {
            return new VideoEntityResponse(
                    item.getValueDataSource(), item.getValueDataSource(), videoType);
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

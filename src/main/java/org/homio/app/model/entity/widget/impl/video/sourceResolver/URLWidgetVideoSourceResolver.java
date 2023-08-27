package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.rest.MediaController;
import org.springframework.stereotype.Component;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String source = item.getValueDataSource();
        try {
            if (Files.exists(Paths.get(source))) {
                return new VideoEntityResponse(source, MediaController.createVideoLink(source));
            }
        } catch (Exception ignore) {
        }
        if (source.startsWith("http") || source.startsWith("$DEVICE_URL")) {
            return new VideoEntityResponse(source, source);
        }
        return null;
    }
}

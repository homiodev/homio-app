package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import static org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse.getVideoType;

import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.springframework.stereotype.Component;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String dataSource = item.getValueDataSource();
        if (dataSource.startsWith("http") || dataSource.startsWith("$DEVICE_URL")) {
            return new VideoEntityResponse(dataSource, dataSource, getVideoType(dataSource));
        }
        return null;
    }
}

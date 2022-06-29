package org.touchhome.app.model.entity.widget.impl.video.sourceResolver;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;

import java.util.Collection;

public interface WidgetVideoSourceResolver {

    VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item);

    @Getter
    class VideoEntityResponse {
        private final String dataSource;
        private final String source;
        private final String type;
        @Setter
        private Collection<UIInputEntity> actions;

        public VideoEntityResponse(String dataSource, String source, String type) {
            this.dataSource = dataSource;
            this.source = source;
            this.type = type;
        }
    }
}

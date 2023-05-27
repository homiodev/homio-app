package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.video.BaseVideoStreamEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoStreamWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    private final EntityContext entityContext;

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String ds = item.getValueDataSource();
        String[] keys = ds.split("~~~");
        BaseVideoStreamEntity baseVideoStreamEntity = entityContext.getEntity(keys[0]);
        if (baseVideoStreamEntity != null) {
            VideoEntityResponse response =
                new VideoEntityResponse(ds, baseVideoStreamEntity.getStreamUrl(keys[1]), null);
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            baseVideoStreamEntity.assembleActions(uiInputBuilder);
            response.setActions(uiInputBuilder.buildAll());
        }
        return null;
    }
}

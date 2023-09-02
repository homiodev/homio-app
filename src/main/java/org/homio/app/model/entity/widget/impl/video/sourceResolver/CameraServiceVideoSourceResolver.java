package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import static org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse.getVideoType;

import lombok.RequiredArgsConstructor;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.api.EntityContext;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CameraServiceVideoSourceResolver implements WidgetVideoSourceResolver {

    private final EntityContext entityContext;

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String ds = item.getValueDataSource();
        String[] keys = ds.split("~~~");
        BaseVideoEntity<?, ?> baseVideoStreamEntity = entityContext.getEntity(keys[0]);
        if (baseVideoStreamEntity != null) {
            String url = getUrl(keys[1], keys[0]);
            VideoEntityResponse response = new VideoEntityResponse(ds, url, getVideoType(url));
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            baseVideoStreamEntity.assembleActions(uiInputBuilder);
            response.setActions(uiInputBuilder.buildAll());
            if(!baseVideoStreamEntity.isStart()) {
                response.setError("W.ERROR.VIDEO_NOT_STARTED");
            }
            return response;
        }
        return null;
    }

    public String getUrl(String path, String entityID) {
        return "$DEVICE_URL/rest/media/video/%s/%s".formatted(entityID, path);
    }
}

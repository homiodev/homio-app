package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import static org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse.getVideoType;

import lombok.RequiredArgsConstructor;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.api.Context;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CameraServiceVideoSourceResolver implements WidgetVideoSourceResolver {

    private final Context context;

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String ds = DataSourceUtil.getSelection(item.getValueDataSource()).getValue();
        String[] keys = ds.split("-->");
        String entityID = keys[0];
        BaseCameraEntity<?, ?> baseVideoStreamEntity = context.db().getEntity(entityID);
        if (baseVideoStreamEntity != null && keys.length >= 2) {
            String videoIdentifier = keys[keys.length - 1];
            String url = getUrl(videoIdentifier, entityID);
            VideoEntityResponse response = new VideoEntityResponse(item.getValueDataSource(), ds, url, getVideoType(url));
            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
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

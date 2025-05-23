package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.RequiredArgsConstructor;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.api.Context;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.utils.MediaUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CameraServiceVideoSourceResolver implements WidgetVideoSourceResolver {

  private final Context context;

  @Override
  public VideoEntityResponse resolveDataSource(String valueDataSource, Context context) {
    String ds = DataSourceUtil.getSelection(valueDataSource).getValue();
    String[] keys = ds.split("-->");
    String entityID = keys[0];
    DeviceBaseEntity entity = context.db().get(entityID);
    if (entity != null && keys.length >= 2) {
      String videoIdentifier = keys[keys.length - 1];
      if (videoIdentifier.startsWith("http") || videoIdentifier.startsWith("$DEVICE_URL")) {
        return new VideoEntityResponse(valueDataSource, videoIdentifier, videoIdentifier, MediaUtils.getVideoType(videoIdentifier));
      }
      String url = getUrl(videoIdentifier, entityID);
      VideoEntityResponse response = new VideoEntityResponse(valueDataSource, ds, url, MediaUtils.getVideoType(url));

      if (entity instanceof BaseCameraEntity<?, ?> camera) {
        UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
        camera.assembleActions(uiInputBuilder);
        response.setActions(uiInputBuilder.buildAll());
        if (!camera.isStart()) {
          response.setError("W.ERROR.VIDEO_NOT_STARTED");
        }
      }
      return response;
    }
    return null;
  }

  public String getUrl(String path, String entityID) {
    return "$DEVICE_URL/rest/media/video/%s/%s".formatted(entityID, path);
  }
}

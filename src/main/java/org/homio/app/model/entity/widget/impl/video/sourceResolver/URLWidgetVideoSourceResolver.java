package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import org.homio.api.Context;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.impl.URLContentStream;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.utils.MediaUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.net.MalformedURLException;
import java.time.Duration;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

  @Override
  public VideoEntityResponse resolveDataSource(String valueDataSource, Context context) {
    String dataSource = DataSourceUtil.getSelection(valueDataSource).getValue();
    if (dataSource.startsWith("http")) {
      try {
        ContentStream contentStream = new URLContentStream(dataSource) {
          @Override
          public @NotNull String createStreamUrl(@NotNull ContentStream contentStream) {
            String streamUrl = context.media().createStreamUrl(contentStream, Duration.ofMinutes(60));
            return streamUrl.substring(streamUrl.lastIndexOf("/") + 1);
          }
        };
        MimeType mimeType = contentStream.getStreamFormat().getMimeType();
        String fsSource = "$DEVICE_URL/" + context.media().createStreamUrl(contentStream, Duration.ofDays(31));
        return new VideoEntityResponse(valueDataSource, valueDataSource, fsSource, mimeType.toString());
      } catch (MalformedURLException ignored) {
      }
    }
    if (dataSource.startsWith("$DEVICE_URL")) {
      String videoType = MediaUtils.getVideoType(dataSource);
      return new VideoEntityResponse(valueDataSource, dataSource, dataSource, videoType);
    }
    return null;
  }
}

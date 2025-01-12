package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.homio.api.Context;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.stream.impl.ResourceContentStream;
import org.homio.api.stream.video.VideoFormat;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.service.FileSystemService;
import org.homio.app.utils.MediaUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FSWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

  private final FileSystemService fileSystemService;

  @Override
  public VideoEntityResponse resolveDataSource(String valueDataSource, Context context) {
    SelectionSource selection = DataSourceUtil.getSelection(valueDataSource);
    if (selection.getMetadata().path("type").asText().equals("file")) {
      String resource = selection.getValue();
      String fs = selection.getMetadata().path("fs").asText();
      int alias = selection.getMetadata().path("alias").asInt(0);

      FileSystemProvider fileSystem = fileSystemService.getFileSystem(fs, alias);
      if (fileSystem != null && fileSystem.exists(resource)) {
        String extension = Objects.toString(FilenameUtils.getExtension(resource), "");
        String videoType = MediaUtils.getVideoType(resource);
        if (VIDEO_FORMATS.matcher(extension).matches()) {
          String fsSource = createVideoPlayLink(fileSystem, resource, context, videoType);
          return new VideoEntityResponse(valueDataSource, valueDataSource, fsSource, videoType);
        } else if (IMAGE_FORMATS.matcher(extension).matches()) {
          String fsSource = "$DEVICE_URL/rest/media/image/%s?fs=%s".formatted(resource, fs);
          return new VideoEntityResponse(valueDataSource, valueDataSource, fsSource, videoType);
        } else {
          return new VideoEntityResponse(valueDataSource, valueDataSource, "", videoType)
            .setError(extension + " format not supported yet");
        }
      }
    }
    return null;
  }

  @SneakyThrows
  private String createVideoPlayLink(FileSystemProvider fileSystem, String id, Context context, String videoType) {
    var resource = fileSystem.getEntryResource(id);
    var format = new VideoFormat(MediaType.parseMediaType(videoType));
    var stream = new ResourceContentStream(resource, format);
    return "$DEVICE_URL/" + context.media().createStreamUrl(stream, Duration.ofDays(31));
  }
}

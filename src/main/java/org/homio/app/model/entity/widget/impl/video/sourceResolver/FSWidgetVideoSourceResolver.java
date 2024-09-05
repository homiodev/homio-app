package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.rest.FileSystemController.NodeRequest;
import org.homio.app.service.FileSystemService;
import org.homio.app.utils.MediaUtils;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Objects;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Component
@RequiredArgsConstructor
public class FSWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    private final FileSystemService fileSystemService;

    @Override
    public VideoEntityResponse resolveDataSource(String valueDataSource) {
        SelectionSource selection = DataSourceUtil.getSelection(valueDataSource);
        if (selection.getMetadata().path("type").asText().equals("file")) {
            String resource = selection.getValue();
            String fs = selection.getMetadata().path("fs").asText();

            FileSystemProvider fileSystem = fileSystemService.getFileSystem(fs, selection.getMetadata().path("alias").asInt(0));
            if (fileSystem != null && fileSystem.exists(resource)) {
                String extension = Objects.toString(FilenameUtils.getExtension(resource), "");
                String videoType = MediaUtils.getVideoType(resource);
                if (VIDEO_FORMATS.matcher(extension).matches()) {
                    String fsSource = createVideoPlayLink(fileSystem, resource);
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
    private String createVideoPlayLink(FileSystemProvider fileSystem, String resource) {
        byte[] content = OBJECT_MAPPER.writeValueAsBytes(
                new NodeRequest(resource)
                        .setAlias(fileSystem.getFileSystemAlias())
                        .setSourceFs(fileSystem.getFileSystemId()));
        byte[] encodedBytes = Base64.getEncoder().encode(content);
        String id = new String(encodedBytes);
        return "$DEVICE_URL/rest/media/video/" + id + "/play";
    }
}

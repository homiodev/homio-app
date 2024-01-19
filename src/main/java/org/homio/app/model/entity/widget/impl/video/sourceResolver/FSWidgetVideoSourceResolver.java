package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import static org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse.getVideoType;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.rest.MediaController;
import org.homio.app.service.FileSystemService;
import org.springframework.stereotype.Component;

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

            FileSystemProvider fileSystem = fileSystemService.getFileSystem(fs, 0);
            if (fileSystem != null && fileSystem.exists(resource)) {
                String extension = StringUtils.defaultString(FilenameUtils.getExtension(resource));
                String videoType = getVideoType(resource);
                if (VIDEO_FORMATS.matcher(extension).matches()) {
                    String fsSource = MediaController.createVideoPlayLink(fileSystem, resource, videoType, extension);
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
}

package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import static org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse.getVideoType;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.homio.api.fs.FileSystemProvider;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.rest.MediaController;
import org.homio.app.service.FileSystemService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FSWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    private final FileSystemService fileSystemService;
    private final Pattern SUPPORTED_FORMATS = Pattern.compile("webm|ogv|flv|avi|mp4|ts|jpg|m3u8|mpd");

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String dataSource = item.getValueDataSource();
        if (dataSource.startsWith("file://")) {
            String sourcePathAndFS = dataSource.substring("file://".length());

            String[] items = sourcePathAndFS.split("###");
            String resource = items[0];
            String fs = items[1];

            FileSystemProvider fileSystem = fileSystemService.getFileSystem(fs);
            if (fileSystem != null && fileSystem.exists(resource)) {
                String extension = FilenameUtils.getExtension(resource);
                String videoType = getVideoType(resource);
                if (SUPPORTED_FORMATS.matcher(extension).matches()) {
                    String fsSource = MediaController.createVideoPlayLink(fileSystem, resource, videoType, extension);
                    return new VideoEntityResponse(dataSource, fsSource, videoType);
                } else {
                    return new VideoEntityResponse(dataSource, "", videoType)
                        .setError(extension + " format not supported yet");
                }
            }
        }
        return null;
    }
}

package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.homio.api.fs.FileSystemProvider;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.rest.MediaController;
import org.homio.app.service.FileSystemService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    private final FileSystemService fileSystemService;

    @Override
    public VideoEntityResponse resolveDataSource(WidgetVideoSeriesEntity item) {
        String source = item.getValueDataSource();
        if (source.startsWith("file://")) {
            String sourcePathAndFS = source.substring("file://".length());

            String[] items = sourcePathAndFS.split("###");
            String resource = items[0];
            String fs = items[1];

            FileSystemProvider fileSystem = fileSystemService.getFileSystem(fs);
            if (fileSystem != null && fileSystem.exists(resource)) {
                String extension = FilenameUtils.getExtension(resource);
                switch (extension) {
                    case "webm", "ogv", "flv", "avi", "mp4" -> {
                        return new VideoEntityResponse(resource, MediaController.createVideoPlayLink(fileSystem, resource));
                    }
                    case "jpg", "m3u8" -> {
                        String fsSource = "$DEVICE_URL/rest/fs/%s/download/%s".formatted(fs, resource);
                        return new VideoEntityResponse(resource, fsSource);
                    }
                    case "mpd" -> throw new IllegalStateException("Not supported format yet");
                }
            }
        }
        if (source.startsWith("http") || source.startsWith("$DEVICE_URL")) {
            return new VideoEntityResponse(source, source);
        }
        return null;
    }
}

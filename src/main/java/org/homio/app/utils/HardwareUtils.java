package org.homio.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.util.CommonUtils;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.FSWidgetVideoSourceResolver;
import org.jetbrains.annotations.NotNull;

@Log4j2
public final class HardwareUtils {

    public static @NotNull String getVideoType(String url) {
        if (url.startsWith("https://youtu")) {
            return "video/youtube";
        }
        if (url.startsWith("https://vimeo")) {
            return "video/vimeo";
        }
        if (url.endsWith(".ts")) {
            return "video/MP2T";
        }
        if (url.endsWith(".ogv")) {
            return "video/ogg";
        }
        if (url.endsWith(".m3u8")) {
            return "application/x-mpegURL";
        }
        if (url.endsWith(".mpd")) {
            return "application/dash+xml";
        }
        String extension = StringUtils.defaultString(FilenameUtils.getExtension(url));
        if (FSWidgetVideoSourceResolver.IMAGE_FORMATS.matcher(extension).matches()) {
            return "image/" + extension;
        }
        if (FSWidgetVideoSourceResolver.VIDEO_FORMATS.matcher(extension).matches()) {
            return "video/" + extension;
        }
        return "video/unknown";
    }

    @SneakyThrows
    public static void copyResources(URL url) {
        if (url != null) {
            Path target = CommonUtils.getFilesPath();
            InputStream stream = HardwareUtils.class.getClassLoader().getResourceAsStream(url.toString());
            FileSystem fileSystem = null;
            if (stream == null) {
                fileSystem = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap());
                Path filesPath = fileSystem.getPath("external_files.7z");
                stream = Files.exists(filesPath) ? Files.newInputStream(filesPath) : null;
            }
            if (stream != null) {
                String addonJar = url.getFile().replaceAll(".jar!/", "_");
                addonJar = addonJar.substring(addonJar.lastIndexOf("/") + 1);
                Path targetPath = target.resolve(target.resolve(addonJar));
                if (!Files.exists(targetPath) || Files.size(targetPath) != stream.available()) {
                    // copy files
                    log.info("Copy resource <{}>", url);
                    Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Unzip resource <{}>", targetPath);
                    ArchiveUtil.unzip(targetPath, targetPath.getParent(), null, false, null, ArchiveUtil.UnzipFileIssueHandler.replace);
                    // Files.move();
                    log.info("Done copy resource <{}>", url);
                }
                stream.close();
                if (fileSystem != null) {
                    fileSystem.close();
                }
            }
        }
    }
}

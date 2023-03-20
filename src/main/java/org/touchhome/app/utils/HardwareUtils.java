package org.touchhome.app.utils;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.fs.archive.ArchiveUtil;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Log4j2
public final class HardwareUtils {

    @SneakyThrows
    public static void copyResources(URL url) {
        if (url != null) {
            Path target = TouchHomeUtils.getFilesPath();
            InputStream stream = HardwareUtils.class.getClassLoader().getResourceAsStream(url.toString());
            FileSystem fileSystem = null;
            if (stream == null) {
                fileSystem = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap());
                Path filesPath = fileSystem.getPath("external_files.7z");
                stream = Files.exists(filesPath) ? Files.newInputStream(filesPath) : null;
            }
            if (stream != null) {
                String bundleJar = url.getFile().replaceAll(".jar!/", "_");
                bundleJar = bundleJar.substring(bundleJar.lastIndexOf("/") + 1);
                Path targetPath = target.resolve(target.resolve(bundleJar));
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

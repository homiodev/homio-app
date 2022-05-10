package org.touchhome.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.util.CommonUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;

@Log4j2
public final class HardwareUtils {

    private static boolean hardwareChecked = false;

    /**
     * This method fires before ApplicationContext startup to make sure all related dependencies up
     */
    public static void prepareHardware(ConfigurableListableBeanFactory beanFactory) {
        if (hardwareChecked) {
            return;
        }
        hardwareChecked = true;
        copyResources();
    }

    @SneakyThrows
    private static void copyResources() {
        URL url = HardwareUtils.class.getClassLoader().getResource("asm_files");
        if (url == null) {
            throw new RuntimeException("Unable to find 'asm_files' directory.");
        }
        copyResources(url, "/BOOT-INF/classes/asm_files");
    }

    @SneakyThrows
    public static void copyResources(URL url, String jarFiles) {
        if (url != null) {
            Path target = TouchHomeUtils.getFilesPath();
            if (url.getProtocol().equals("jar")) {
                try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
                    Files.walk(fs.getPath(jarFiles)).filter(f -> Files.isRegularFile(f)).forEach((Path path) -> {
                        try {
                            Path resolve = target.resolve(path.toString().substring(jarFiles.length() + 1));
                            Files.createDirectories(resolve.getParent());
                            if (!Files.exists(resolve) || (Files.exists(resolve) && Files.getLastModifiedTime(resolve).compareTo(Files.getLastModifiedTime(path)) < 0)) {
                                if (mayCopyResource(path.getFileName().toString())) {
                                    log.info("Copy resource <{}>", path.getFileName());
                                    Files.copy(path, resolve, StandardCopyOption.REPLACE_EXISTING);
                                    if (path.getFileName().toString().endsWith(".zip")) {
                                        log.info("Unzip resource <{}>", path.getFileName());
                                        CommonUtils.unzip(resolve, resolve.getParent());
                                        log.info("Done unzip resource <{}>", path.getFileName());
                                    }
                                    log.info("Done copy resource <{}>", path.getFileName());
                                } else {
                                    log.warn("Skip copying resource <{}>", path.getFileName());
                                }
                            } else {
                                log.info("Skip copy resource <{}>", path.getFileName());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } else {
                FileUtils.copyDirectory(new File(url.toURI()), target.toFile(), false);
            }
        }
    }

    // not to smart but works well :)
    private static boolean mayCopyResource(String fileName) {
        if (fileName.endsWith("_filter.zip")) {
            if (SystemUtils.IS_OS_LINUX && !fileName.endsWith(".avr_filter.zip")) {
                return false;
            }
            return !SystemUtils.IS_OS_WINDOWS || fileName.endsWith(".win_filter.zip");
        }
        return true;
    }
}

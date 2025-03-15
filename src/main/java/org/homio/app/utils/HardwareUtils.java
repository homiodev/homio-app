package org.homio.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.util.CommonUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

@Log4j2
public final class HardwareUtils {

  /**
   * Fully restart application
   */
  @SneakyThrows
  public static void exitApplication(ApplicationContext applicationContext, int code) {
    SpringApplication.exit(applicationContext, () -> code);
    System.exit(code);
    // sleep to allow program exist
    Thread.sleep(30000);
    log.info("Unable to stop app in 30sec. Force stop it");
    // force exit
    Runtime.getRuntime().halt(code);
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

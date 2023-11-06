package org.homio.addon.firmata.arduino;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.firmata.arduino.contributions.ConsoleProgressListener;
import org.homio.addon.firmata.arduino.contributions.GPGDetachedSignatureVerifier;
import org.homio.addon.firmata.arduino.contributions.libraries.LibraryInstaller;
import org.homio.addon.firmata.arduino.contributions.packages.ContributionInstaller;
import org.homio.addon.firmata.arduino.files.DeleteFilesOnShutdown;
import org.homio.addon.firmata.platform.BaseNoGui;
import org.homio.addon.firmata.platform.Platform;
import org.homio.addon.firmata.platform.PreferencesData;
import org.homio.api.fs.archive.ArchiveUtil;

@Log4j2
public class ArduinoConfiguration {

  public static Platform platform;
  private static LibraryInstaller libraryInstaller;
  private static ContributionInstaller contributionInstaller;
  private static GPGDetachedSignatureVerifier gpgDetachedSignatureVerifier = new GPGDetachedSignatureVerifier();

  public static Platform getPlatform() {
    if (platform == null) {
      try {
        Path arduinoPath = Paths.get(System.getProperty("APP_DIR"));

        if (!ArchiveUtil.isValidArchive(arduinoPath.resolve("arduino-dependencies.7z"))) {
          log.warn("Skip creating ardiono platform because no dependencies installed");
          return null;
        }

        log.info("Create arduino platform. App path: <{}>", arduinoPath);
        BaseNoGui.initPlatform();

        Thread deleteFilesOnShutdownThread = new Thread(DeleteFilesOnShutdown.INSTANCE);
        deleteFilesOnShutdownThread.setName("DeleteFilesOnShutdown");
        Runtime.getRuntime().addShutdownHook(deleteFilesOnShutdownThread);

        BaseNoGui.getPlatform().init();

        BaseNoGui.initPortableFolder();

        File hardwareFolder = BaseNoGui.getHardwareFolder();
        PreferencesData.set("runtime.ide.path", hardwareFolder.getParentFile().getAbsolutePath());
        PreferencesData.set("runtime.ide.version", "" + BaseNoGui.REVISION);

        PreferencesData.set("build.path", arduinoPath.resolve("build").toString());
        PreferencesData.set("sketchbook.path", "sketchbook");

        PreferencesData.set("compiler.warning_level", "none");
        PreferencesData.setBoolean("compiler.cache_core", true);
        PreferencesData.setBoolean("cache.enable", true);
        PreferencesData.setInteger("build.warn_data_percentage", 75);

        BaseNoGui.checkInstallationFolder();

        BaseNoGui.initVersion();

        BaseNoGui.initPackages();
        BaseNoGui.onBoardOrPortChange();

        platform = BaseNoGui.getPlatform();
        log.info("Arduino platform success created");
      } catch (Exception ex) {
        log.warn("Error while init arduino platform", ex);
      }
    }
    return platform;
  }

  public static ContributionInstaller getContributionInstaller() {
    if (contributionInstaller == null) {
      try {
        contributionInstaller = new ContributionInstaller(getPlatform(), gpgDetachedSignatureVerifier);

        Set<String> packageIndexURLs =
            new HashSet<>(PreferencesData.getCollection(Constants.PREF_BOARDS_MANAGER_ADDITIONAL_URLS));
        packageIndexURLs.add("http://arduino.esp8266.com/stable/package_esp8266com_index.json");
        PreferencesData.setCollection(Constants.PREF_BOARDS_MANAGER_ADDITIONAL_URLS, packageIndexURLs);

        List<String> downloadedPackageIndexFiles = contributionInstaller.updateIndex(new ConsoleProgressListener());
        contributionInstaller.deleteUnknownFiles(downloadedPackageIndexFiles);
        BaseNoGui.initPackages();
      } catch (Exception ex) {
        log.error("Error create ContributionInstaller", ex);
        throw new RuntimeException("Unable to create arduino ContributionInstaller");
      }
    }
    return contributionInstaller;
  }

  public static LibraryInstaller getLibraryInstaller() {
    if (libraryInstaller == null) {
      try {
        libraryInstaller = new LibraryInstaller(getPlatform(), gpgDetachedSignatureVerifier);
      } catch (Exception ex) {
        log.warn("Error while create library installer");
      }
    }
    return libraryInstaller;
  }
}

package org.touchhome.app.camera.util;

import lombok.extern.log4j.Log4j2;
import org.touchhome.app.camera.setting.CameraFFMPEGInstallPathOptions;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class FFMPEGDependencyExecutableInstaller implements DependencyExecutableInstaller {

    @Override
    public boolean isRequireInstallDependencies(EntityContext entityContext) {
        MachineHardwareRepository repository = entityContext.getBean(MachineHardwareRepository.class);
        if (repository.isSoftwareInstalled("ffmpeg")) {
            return false;
        }
        Path ffmpegPath = entityContext.setting().getValue(CameraFFMPEGInstallPathOptions.class);
        if (ffmpegPath != null && Files.isRegularFile(ffmpegPath)) {
            return !repository.execute(ffmpegPath + " -version").startsWith("ffmpeg version 2");
        }
        return true;
    }

    @Override
    public void installDependencyInternal(EntityContext entityContext, String progressKey) {
        if (TouchHomeUtils.OS_NAME.isLinux()) {
            entityContext.getBean(MachineHardwareRepository.class).installSoftware("ffmpeg");
        } else {
            Path targetFolder = downloadAndExtract("https://bintray.com/touchhome/touchhome/download_file?file_path=ffmpeg.7z",
                    "7z", "ffmpeg", progressKey, entityContext, log);
            entityContext.setting().setValue(CameraFFMPEGInstallPathOptions.class, targetFolder.resolve("ffmpeg.exe"));
        }
    }

    @Override
    public void afterDependencyInstalled() {

    }
}

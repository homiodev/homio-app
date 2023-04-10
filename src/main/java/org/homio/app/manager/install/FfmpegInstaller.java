package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.bundle.api.util.CommonUtils.FFMPEG_LOCATION;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.homio.app.config.AppProperties;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.Curl;
import org.homio.bundle.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class FfmpegInstaller extends DependencyExecutableInstaller {

    public FfmpegInstaller(EntityContext entityContext) {
        super(entityContext);
    }

    @Override
    public String getName() {
        return "ffmpeg";
    }

    @Override
    protected @Nullable String getInstalledVersion() {
        MachineHardwareRepository repository = entityContext.getBean(MachineHardwareRepository.class);
        if (IS_OS_WINDOWS) {
            Path targetPath = CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe");
            if (Files.isRegularFile(targetPath)) {
                return repository.executeNoErrorThrow(targetPath + " -v", 60, null);
            }
        }
        return repository.executeNoErrorThrow("ffmpeg -v", 60, null);
    }

    @Override
    public @Nullable String getExecutablePath(@NotNull String execPath) {
        return getVersion() == null ? null : FFMPEG_LOCATION;
    }

    @Override
    protected @Nullable Path installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            MachineHardwareRepository repository = entityContext.getBean(MachineHardwareRepository.class);
            if (!repository.isSoftwareInstalled("ffmpeg")) {
                repository.installSoftware("ffmpeg", 600);
            }
        } else {
            Curl.downloadAndExtract(entityContext.getBean(AppProperties.class).getSource().getFfmpeg(), "ffmpeg.7z",
                (progress, message) -> {
                    progressBar.progress(progress, message);
                    log.info("ffmpeg " + message + ". " + progress + "%");
                }, log);
        }
        return Path.of(FFMPEG_LOCATION);
    }
}

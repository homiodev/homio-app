package org.homio.app.manager.install;

import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.app.manager.common.impl.EntityContextMediaImpl.FFMPEG_LOCATION;

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
    public @Nullable String getExecutablePath(@NotNull String execPath) {
        return getVersion() == null ? null : FFMPEG_LOCATION;
    }

    @Override
    protected @Nullable String getInstalledVersion() {
        EntityContextHardware hardware = entityContext.hardware();
        String version = null;
        if (IS_OS_WINDOWS) {
            Path targetPath = CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe");
            if (Files.isRegularFile(targetPath)) {
                version = hardware.executeNoErrorThrow(targetPath + " -v", 60, null);
            }
        } else {
            version = hardware.executeNoErrorThrow("ffmpeg -v", 60, null);
        }
        return version;
    }

    @Override
    protected @Nullable Path installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            EntityContextHardware hardware = entityContext.hardware();
            if (!hardware.isSoftwareInstalled("ffmpeg")) {
                hardware.installSoftware("ffmpeg", 600);
            }
        } else {
            String url = entityContext.setting().getEnvRequire("source-ffmpeg", String.class,
                    "https://github.com/homiodev/static-files/raw/master/ffmpeg.7z", true);
            CommonUtils.downloadAndExtract(url, "ffmpeg.7z",
                    (progress, message, error) -> {
                        progressBar.progress(progress, message);
                        log.info("FFMPEG: {}", message);
                    });
        }
        return Path.of(FFMPEG_LOCATION);
    }
}

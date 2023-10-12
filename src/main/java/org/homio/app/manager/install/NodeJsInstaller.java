package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.CommonUtils.STATIC_FILES;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware;
import org.homio.api.repository.GitHubProject.VersionedFile;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class NodeJsInstaller extends DependencyExecutableInstaller {

    public NodeJsInstaller(EntityContext entityContext) {
        super(entityContext);
    }

    @Override
    public String getName() {
        return "nodejs";
    }

    @Override
    protected @Nullable String getInstalledVersion() {
        EntityContextHardware hardware = entityContext.hardware();
        if (IS_OS_WINDOWS) {
            Path targetPath = CommonUtils.getInstallPath().resolve("nodejs").resolve("node.exe");
            if (Files.isRegularFile(targetPath)) {
                executable = targetPath.toString();
                return hardware.executeNoErrorThrow(targetPath + " -v", 60, null);
            }
        }
        executable = "node";
        return hardware.executeNoErrorThrow("node -v", 60, null);
    }

    @Override
    protected void installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            EntityContextHardware hardware = entityContext.hardware();
            hardware.execute("curl -fsSL https://deb.nodesource.com/setup_current.x | sudo -E bash -");
            hardware.installSoftware("nodejs", 600);
        } else {
            String url = entityContext.setting().getEnv("source-nodejs");
            if (url == null) {
                url = STATIC_FILES.getContentFile("nodejs").map(VersionedFile::getDownloadUrl).orElse(null);
            }
            if (url == null) {
                throw new IllegalStateException("Unable to find nodejs download url");
            }
            CommonUtils.downloadAndExtract(url,
                    "nodejs.7z", (progress, message, error) -> {
                        progressBar.progress(progress, message);
                        log.info("NodeJS: {}", message);
                    });
        }
    }
}

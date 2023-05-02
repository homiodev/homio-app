package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.homio.app.config.AppProperties;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextHardware;
import org.homio.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.Curl;
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
                return hardware.executeNoErrorThrow(targetPath + " -v", 60, null);
            }
        }
        return hardware.executeNoErrorThrow("node -v", 60, null);
    }

    @Override
    protected @Nullable Path installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            EntityContextHardware hardware = entityContext.hardware();
            hardware.execute("curl -fsSL https://deb.nodesource.com/setup_current.x | sudo -E bash -");
            hardware.installSoftware("nodejs", 600);
        } else {
            Path path = Curl.downloadAndExtract(entityContext.getBean(AppProperties.class).getSource().getNode(),
                "nodejs.7z", (progress, message) -> {
                    progressBar.progress(progress, message);
                    log.info("nodejs " + message + ". " + progress + "%");
                }, log);
            return Objects.requireNonNull(path).resolve("node.exe");
        }
        return null;
    }
}

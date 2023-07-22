package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware;
import org.homio.api.entity.dependency.DependencyExecutableInstaller;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class MosquittoInstaller extends DependencyExecutableInstaller {

    public MosquittoInstaller(EntityContext entityContext) {
        super(entityContext);
    }

    @Override
    public String getName() {
        return "mosquitto";
    }

    @Override
    protected @Nullable String getInstalledVersion() {
        EntityContextHardware hardware = entityContext.hardware();
        List<String> versionList = null;
        if (IS_OS_WINDOWS) {
            Path mosquittoPath = CommonUtils.getInstallPath().resolve("mosquitto").resolve("mosquitto.exe");
            String mosquittoDir = Files.isRegularFile(mosquittoPath) ? mosquittoPath.toString() :
                System.getProperty("MOSQUITTO_DIR", System.getenv("MOSQUITTO_DIR"));
            if (StringUtils.isNotEmpty(mosquittoDir)) {
                versionList = hardware.executeNoErrorThrowList(mosquittoDir + " -h", 10, null);
            }
        }
        if (versionList == null || versionList.isEmpty()) {
            versionList = hardware.executeNoErrorThrowList("mosquitto -h", 60, null);
        }
        if (!versionList.isEmpty() && versionList.get(0).startsWith("mosquitto version")) {
            return versionList.get(0).substring("mosquitto version".length()).trim();
        }
        return null;
    }

    @Override
    protected @Nullable Path installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            entityContext.hardware().installSoftware(getName(), 600);
        } else {
            String url = entityContext.setting().getEnvRequire("source-mosquitto", String.class,
                "https://github.com/homiodev/static-files/raw/master/mosquitto.7z", true);
            CommonUtils.downloadAndExtract(url,
                "mosquitto.7z", (progress, message, error) -> {
                    progressBar.progress(progress, message);
                    log.info("Mosquitto: {}", message);
                });
            return CommonUtils.getInstallPath().resolve("mosquitto").resolve("mosquitto.exe");
        }
        return null;
    }
}

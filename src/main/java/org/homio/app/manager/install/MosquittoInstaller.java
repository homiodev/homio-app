package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
        MachineHardwareRepository machineHardwareRepository = entityContext.getBean(MachineHardwareRepository.class);
        List<String> versionList = null;
        if (IS_OS_WINDOWS) {
            Path mosquittoPath = CommonUtils.getInstallPath().resolve("mosquitto").resolve("mosquitto.exe");
            if (Files.isRegularFile(mosquittoPath)) {
                versionList = machineHardwareRepository.executeNoErrorThrowList(mosquittoPath + " -h", 10, null);
            }
        }
        if (versionList == null || versionList.isEmpty()) {
            versionList = machineHardwareRepository.executeNoErrorThrowList("mosquitto -h", 60, null);
        }
        if (versionList != null && !versionList.isEmpty() && versionList.get(0).startsWith("mosquitto version")) {
            return versionList.get(0).substring("mosquitto version".length()).trim();
        }
        return null;
    }

    @Override
    protected @Nullable Path installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        if (IS_OS_LINUX) {
            MachineHardwareRepository machineHardwareRepository = entityContext.getBean(MachineHardwareRepository.class);
            machineHardwareRepository.installSoftware(getName(), 600);
        } else {
            Path path = Curl.downloadAndExtract(entityContext.getBean(AppProperties.class).getSource().getMosquitto(),
                "mosquitto.7z", (progress, message) -> {
                    progressBar.progress(progress, message);
                    log.info("mosquitto " + message + ". " + progress + "%");
                }, log);
            return Objects.requireNonNull(path).resolve("mosquitto.exe");
        }
        return null;
    }
}
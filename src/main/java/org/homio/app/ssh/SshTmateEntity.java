package org.homio.app.ssh;

import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.service.ssh.SshBaseEntity;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.ssh.service.SshTmateService;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Log4j2
@Entity
@CreateSingleEntity
@UISidebarChildren(icon = "fas fa-satellite-dish", color = "#0088CC", allowCreateItem = false)
public class SshTmateEntity extends SshBaseEntity<SshTmateEntity, SshTmateService> {

    public static void ensureEntityExists(ContextImpl context) {
        SshTmateEntity tmate = context.db().get(SshTmateEntity.class, PRIMARY_DEVICE);
        if (SystemUtils.IS_OS_LINUX) {
            ContextHardware hardware = context.hardware();
            if (!hardware.isSoftwareInstalled("tmate")) {
                context.event().runOnceOnInternetUp("install-tmate", () ->
                        context.bgp().runWithProgress("install-tmate", false).executeSync(progressBar ->
                                installTmate(tmate, hardware, progressBar)));
            }
        }
    }

    @SneakyThrows
    private static void installTmate(SshTmateEntity tmateEntity, ContextHardware repository, ProgressBar progressBar) {
        tmateEntity.setStatus(Status.UPDATING);
        try {
            repository.installSoftware("tmate", 60, progressBar);
        } catch (Exception ex) {
            log.info("Unable to install tmate. Error: {}", ex.getMessage());
            MachineHardwareRepository hardware = repository.context().getBean(MachineHardwareRepository.class);
            String arm = getTmateArm(hardware);
            if (arm != null) {
                Path rootPath = CommonUtils.getTmpPath();
                String url = "https://github.com/tmate-io/tmate/releases/download/2.4.0/tmate-2.4.0-static-linux-%s.tar.xz".formatted(arm);
                Path target = rootPath.resolve("tmate.tar.xz");
                log.info("Download tmate {} to {}", url, target);
                Curl.downloadWithProgress(url, target, progressBar);
                repository.execute("sudo tar -C %s -xvf %s/tmate.tar.xz".formatted(rootPath, rootPath));
                Files.deleteIfExists(target);
                Path unpackedTmate = rootPath.resolve("tmate-2.4.0-static-linux-%s".formatted(arm));
                Files.createDirectories(Paths.get("ssh"));
                Path tmate = Paths.get("/usr/bin/tmate");
                Files.move(unpackedTmate.resolve("tmate"), tmate, StandardCopyOption.REPLACE_EXISTING);
                FileUtils.deleteDirectory(unpackedTmate.toFile());
                hardware.setPermissions(tmate, 755);
            } else {
                log.error("Unable to find device arm");
            }
        } finally {
            tmateEntity.getOrCreateService(repository.context()).ifPresent(ServiceInstance::testServiceWithSetStatus);
        }
    }

    private static String getTmateArm(MachineHardwareRepository repository) {
        String architecture = repository.getMachineInfo().getArchitecture();
        if (architecture.startsWith("armv6")) {
            return "arm32v6";
        } else if (architecture.startsWith("armv7")) {
            return "arm32v7";
        } else if (architecture.startsWith("i386")) {
            return "i386";
        } else if (architecture.startsWith("armv8") || architecture.startsWith("aarch64")) {
            return "arm64v8";
        } else if (architecture.startsWith("x86_64")) {
            return "amd64";
        }
        return null;
    }

    @Override
    public String getDescriptionImpl() {
        return Lang.getServerMessage("TMATE_DESCRIPTION");
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return 0;
    }

    @Override
    public @NotNull Class<SshTmateService> getEntityServiceItemClass() {
        return SshTmateService.class;
    }

    @Override
    public String getDefaultName() {
        return "Tmate SSH";
    }

    @Override
    public @Nullable SshTmateService createService(@NotNull Context context) {
        return new SshTmateService(context, this);
    }

    @Override
    public void configureOptionModel(@NotNull OptionModel optionModel, @NotNull Context context) {
        try {
            if (getService().isOpened()) {
                optionModel.setTitle(Lang.getServerMessage("TITLE.TMATE_DISABLED"));
                optionModel.setDisabled(true);
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ssh-tmate";
    }

    @Override
    @UIFieldIgnore
    public @Nullable String getImageIdentifier() {
        return super.getImageIdentifier();
    }
}

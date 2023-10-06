package org.homio.app.model.entity;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.app.manager.common.impl.EntityContextMediaImpl.FFMPEG_LOCATION;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware;
import org.homio.api.EntityContextHardware.ProcessStat;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.log.HasEntitySourceLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.api.state.StringType;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.homio.api.util.CommonUtils;
import org.homio.app.video.ffmpeg.FFMPEGImpl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-podcast", color = "#2DA844", allowCreateItem = false)
public class FFMPEGEntity extends MediaEntity implements
    HasEntityLog,
    HasEntitySourceLog,
    HasFirmwareVersion,
    DeviceEndpointsBehaviourContractStub {

    private static FfmpegInstaller FFMPEG_INSTALLER;

    private static FfmpegInstaller getFfmpegInstaller(EntityContext entityContext) {
        if (FFMPEG_INSTALLER == null) {
            FFMPEG_INSTALLER = new FfmpegInstaller(entityContext);
        }
        return FFMPEG_INSTALLER;
    }

    public static void ensureEntityExists(EntityContext entityContext) {
        entityContext.install().createInstallContext(FfmpegInstaller.class)
                     .requireAsync(null, (installed, exception) -> {
                         if (installed) {
                             log.info("FFPMEG service successfully installed");
                         }
                     });
        FFMPEGEntity.getFfmpegInstaller(entityContext)
                    .installLatestAsync();

        FFMPEGEntity entity = entityContext.getEntity(FFMPEGEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new FFMPEGEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setJsonData("dis_del", true);
            entity.setJsonData("dis_edit", true);
            entityContext.save(entity);
        }

        entityContext.bgp().registerThreadsPuller("ffmpeg", threadPuller -> {
            for (Map.Entry<String, FFMPEGImpl> threadEntry : FFMPEGImpl.ffmpegMap.entrySet()) {
                FFMPEGImpl ffmpeg = threadEntry.getValue();
                if (ffmpeg.getIsAlive()) {
                    threadPuller.addThread(threadEntry.getKey(), ffmpeg.getDescription(), ffmpeg.getCreationDate(),
                        "running", null,
                        "Command: " + ffmpeg.getCmd()
                    );
                }
            }
        });
    }

    @Override
    public String getDescriptionImpl() {
        return "CAMERA.FFMPEG_DESCRIPTION";
    }

    @Override
    public String getDefaultName() {
        return "Ffmpeg";
    }

    @Override
    public @Nullable Status.EntityStatus getEntityStatus() {
        return null;
    }

    @Override
    @UIFieldIgnore
    public @NotNull Status getStatus() {
        return Status.ONLINE;
    }

    @Override
    public void logBuilder(@NotNull EntityLogBuilder builder) {
    }

    @Override
    public String getFirmwareVersion() {
        return FFMPEGEntity.getFfmpegInstaller(getEntityContext()).getVersion();
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }

    @Override
    public @NotNull List<OptionModel> getLogSources() {
        List<OptionModel> list = new ArrayList<>();
        File[] listFiles = FFMPEGImpl.FFMPEG_LOG_PATH.toFile().listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                try {
                    if (file.length() > 0) {
                        list.add(OptionModel.of(file.getName()));
                    }
                } catch (Exception ignore) {}
            }
        }
        return list;
    }

    @Override
    public @Nullable InputStream getSourceLogInputStream(@NotNull String sourceID) throws Exception {
        return Files.newInputStream(FFMPEGImpl.FFMPEG_LOG_PATH.resolve(sourceID));
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ffmpeg";
    }

    @Override
    public @NotNull Icon getEntityIcon() {
        return new Icon("fas fa-podcast", "#2DA844");
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public @Nullable String getPlace() {
        return super.getPlace();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        Map<String, FfmpegInstanceEndpoint> streams = new HashMap<>();
        for (FFMPEGImpl ffmpeg : FFMPEGImpl.ffmpegMap.values()) {
            streams.put(ffmpeg.getDescription(), new FfmpegInstanceEndpoint(ffmpeg, this));
        }
        return streams;
    }

    @Getter
    public static class FfmpegInstanceEndpoint extends BaseDeviceEndpoint<FFMPEGEntity> {

        private final String description;

        @SneakyThrows
        public FfmpegInstanceEndpoint(FFMPEGImpl ffmpeg, FFMPEGEntity entity) {
            super(new Icon(ffmpeg.getFormat().getIcon(), ffmpeg.getFormat().getColor()),
                "FFMPEG",
                entity.getEntityContext(),
                entity,
                ffmpeg.getDescription(),
                false,
                EndpointType.string);
            this.description = ffmpeg.getCmd();
            if (ffmpeg.getIsAlive()) {
                ProcessStat stat = ffmpeg.getProcessStat(this::updateUI);
                setValue(new StringType("Cpu(%.1f%%) Mem(%.1f%%)".formatted(stat.getCpuUsage(), stat.getMemUsage())), false);
            } else {
                setValue(new StringType("Dead"), false);
            }
        }

        public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
            String html = Arrays.stream(getValue().toString().split(" "))
                                .collect(Collectors.joining("</div><div>", "<div>", "</div>"));
            uiInputBuilder.addInfo("<div class=\"dfc fs14\">%s</div>".formatted(html), InfoType.HTML);
        }
    }

    @Log4j2
    public static class FfmpegInstaller extends DependencyExecutableInstaller {

        public FfmpegInstaller(EntityContext entityContext) {
            super(entityContext);
            executable = FFMPEG_LOCATION;
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
                    version = hardware.executeNoErrorThrow(targetPath + " -version", 60, null);
                }
            } else {
                version = hardware.executeNoErrorThrow("ffmpeg -version", 60, null);
            }
            if (version != null && version.startsWith("ffmpeg version 6.0-essentials")) {
                version = "6.0";
            }
            return version;
        }

        @Override
        protected void installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
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
        }
    }
}

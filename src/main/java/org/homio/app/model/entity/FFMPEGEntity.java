package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.ContextHardware.ProcessStat;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.log.HasEntitySourceLog;
import org.homio.api.entity.types.MediaEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.repository.GitHubProject.VersionedFile;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.homio.api.util.CommonUtils.STATIC_FILES;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;

@Log4j2
@Entity
@CreateSingleEntity(disableEdit = true)
@UISidebarChildren(icon = "fas fa-podcast", color = "#2DA844", allowCreateItem = false)
public class FFMPEGEntity extends MediaEntity implements
        HasEntityLog,
        HasEntitySourceLog,
        HasFirmwareVersion,
        DeviceEndpointsBehaviourContractStub {

    private static FfmpegInstaller FFMPEG_INSTALLER;

    public static void ensureEntityExists(Context context) {
        /*context.install().createInstallContext(FfmpegInstaller.class)
                     .requireAsync(null, (installed, exception) -> {
                         if (installed) {
                             log.info("FFPMEG service successfully installed");
                         }
                     });*/
        FFMPEGEntity.getFfmpegInstaller(context).installLatestAsync();

        FFMPEGImpl.entity = context.db().getEntity(FFMPEGEntity.class, PRIMARY_DEVICE);

        context.bgp().registerThreadsPuller("ffmpeg", threadPuller -> {
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
    public String getFirmwareVersion() {
        return FFMPEGEntity.getFfmpegInstaller(context()).getVersion();
    }

    @Override
    @JsonIgnore
    public @Nullable String getIeeeAddress() {
        return getEntityID();
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
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

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

    private static FfmpegInstaller getFfmpegInstaller(Context context) {
        if (FFMPEG_INSTALLER == null) {
            FFMPEG_INSTALLER = new FfmpegInstaller(context);
        }
        return FFMPEG_INSTALLER;
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
        for (FFMPEGImpl ffmpeg : FFMPEGImpl.ffmpegMap.values()) {
            list.add(OptionModel.of(ffmpeg.getFileLogger().getName()));
        }
        return list;
    }

    @Override
    public @Nullable InputStream getSourceLogInputStream(@NotNull String sourceID) {
        for (FFMPEGImpl ffmpeg : FFMPEGImpl.ffmpegMap.values()) {
            if (ffmpeg.getFileLogger().getName().equals(sourceID)) {
                return ffmpeg.getFileLogger().getFileInputStream();
            }
        }
        return null;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ffmpeg";
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

    @Override
    @UIFieldIgnore
    public @Nullable String getImageIdentifier() {
        return super.getImageIdentifier();
    }

    @Getter
    public static class FfmpegInstanceEndpoint extends BaseDeviceEndpoint<FFMPEGEntity> {

        private final String description;

        @SneakyThrows
        public FfmpegInstanceEndpoint(FFMPEGImpl ffmpeg, FFMPEGEntity entity) {
            super(new Icon(ffmpeg.getFormat().getIcon(), ffmpeg.getFormat().getColor()),
                    "FFMPEG",
                    entity.context(),
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

        public FfmpegInstaller(Context context) {
            super(context);
            executable = FFMPEG_LOCATION;
        }

        @Override
        public String getName() {
            return "ffmpeg";
        }

        @Override
        public @Nullable String getExecutablePath(@NotNull Path execPath) {
            return getVersion() == null ? null : FFMPEG_LOCATION;
        }

        @Override
        protected @Nullable String getInstalledVersion() {
            ContextHardware hardware = context.hardware();
            String version = null;
            try {
                if (IS_OS_WINDOWS) {
                    Path targetPath = CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe");
                    if (Files.isRegularFile(targetPath)) {
                        version = hardware.execute(targetPath + " -version", 60, null);
                    }
                } else {
                    version = hardware.execute("ffmpeg -version", 60, null);
                }
            } catch (Exception ignore) {
            }

            if (version != null && version.startsWith("ffmpeg version")) {
                version = version.substring("ffmpeg version".length()).trim().split(" ")[0].trim();
                if (version.contains("-")) {
                    version = version.substring(0, version.indexOf("-"));
                }
            }
            return version;
        }

        @Override
        protected void installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
            if (IS_OS_LINUX) {
                ContextHardware hardware = context.hardware();
                if (!hardware.isSoftwareInstalled("ffmpeg")) {
                    hardware.installSoftware("ffmpeg", 600, progressBar);
                }
            } else {
                String url = context.setting().getEnv("source-ffmpeg");
                if (url == null) {
                    url = STATIC_FILES.getContentFile("ffmpeg").map(VersionedFile::getDownloadUrl).orElse(null);
                }
                if (url == null) {
                    throw new IllegalStateException("Unable to find ffmpeg download url");
                }
                ArchiveUtil.downloadAndExtract(url, "ffmpeg.7z",
                        (progress, message, error) -> {
                            progressBar.progress(progress, message);
                            log.info("FFMPEG: {}", message);
                        });
            }
        }
    }
}

package org.homio.app.service.video;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;
import static org.homio.app.model.entity.MediaMTXEntity.mediamtxGitHub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ProcessContext;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.UpdatableValue;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MediaMTXService extends ServiceInstance<MediaMTXEntity>
    implements HasEntityIdentifier {

    private final Map<String, String> successRegistered = new ConcurrentHashMap<>();
    private final Map<String, String> pendingRegistrations = new ConcurrentHashMap<>();
    private final @Getter Path configurationPath;
    private @Getter @Nullable String apiURL;
    private @Nullable UpdatableValue<JsonNode> pathData;

    private ProcessContext processContext;
    private ObjectNode configuration;

    private @Getter boolean isRunningLocally;

    public MediaMTXService(@NotNull Context context, @NotNull MediaMTXEntity entity) {
        super(context, entity, true);
        configurationPath = CommonUtils.getConfigPath().resolve("mediamtx.yml");
        context.bgp().addLowPriorityRequest("register-mediamtx", this::scheduleRegisterSources);
    }

    public JsonNode getApiList() {
        if (apiURL == null) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            if (pathData == null) {
                pathData = UpdatableValue.wrap(Curl.get(getApi("paths/list"), JsonNode.class), "paths");
            }
            return pathData.getFreshValue(Duration.ofSeconds(60), () ->
                Curl.sendAsync(Curl.createGetRequest(getApi("paths/list")), JsonNode.class, (data, code) -> {
                    if (code == 200) {
                        pathData.update(data);
                    }
                }));
        } catch (Exception ignore) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String getApi(String path) {
        return "%s/v%s/%s".formatted(apiURL, entity.getApiVersion(), path);
    }

    public void dispose(@Nullable Exception ex) {
        context.bgp().removeLowPriorityRequest("register-mediamtx");
        if (ex != null) {
            this.entity.setStatusError(ex);
        } else {
            if (entity.getStatus() != Status.ERROR) {
                this.entity.setStatus(Status.OFFLINE, entity.getStatusMessage());
            }
        }
        ContextBGP.cancel(processContext);
        log.warn("Dispose mediamtx", ex);

        context.ui().toastr().warn("Dispose mediamtx");
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
        this.dispose(ex);
    }

    @SneakyThrows
    public void updateConfiguration(String mc) {
        configuration = YAML_OBJECT_MAPPER.readValue(mc, ObjectNode.class);
        saveConfiguration();
        initialize();
    }

    @SneakyThrows
    public void resetConfiguration() {
        Path backupFile = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");
        if (!Files.exists(backupFile)) {
            throw new ServerException("W.ERROR.BACKUP_NOT_FOUND");
        }
        Files.copy(backupFile, configurationPath, StandardCopyOption.REPLACE_EXISTING);
        syncConfiguration();
        restartService();
    }

    private void setStatusOnline() {
        entity.setStatusOnline();
        context.bgp().addLowPriorityRequest("register-mediamtx", this::scheduleRegisterSources);
    }

    @Override
    public String isRequireRestartService() {
        if (!entity.getStatus().isOnline()) {return "Status: " + entity.getStatus();}
        int status = getApiStatus();
        if (status != 200) {return "API status[%s]".formatted(status);}
        return null;
    }

    public void addSource(@NotNull String path, @NotNull String source) {
        if (!successRegistered.containsKey(path) && !pendingRegistrations.containsKey(path)) {
            pendingRegistrations.put(path, source);
            scheduleRegisterSources();
        }
    }

    public void removeSource(String path) {
        pendingRegistrations.remove(path);
        String source = successRegistered.remove(path);
        // if source was registered already
        if (source == null) {
            String url = getApi("config/paths/remove/%s".formatted(path));
            HttpRequest request = Curl.createPostRequest(url);
            Curl.sendAsync(request, Void.class, (unused, code) -> {
                if (code == 200) {
                    log.error("[{}]: MediaMTX: Video source with path /{} successfully removed", entityID, path);
                } else {
                    log.error("[{}]: MediaMTX: Unable to remove video source with path /{}", entityID, path);
                }
            });
        }
    }

    private boolean isMediaMtxAvailable(String path) {
        for (JsonNode node : getApiList().get("items")) {
            if (node.get("name").asText().equals(path) && node.get("ready").asBoolean()) {
                return true;
            }
        }
        return false;
    }

    @SneakyThrows
    public @NotNull ActionResponseModel updateFirmware(ProgressBar progressBar, String version) {
        return mediamtxGitHub.updateProject("mediamtx", progressBar, true, projectUpdate -> {
            dispose(null);
            Thread.sleep(1000); // wait to finish process. not sure if it's need

            try {
                projectUpdate.getGitHubProject().deleteProject();
            } catch (Exception ex) {
                context().ui().toastr().error(
                    Lang.getServerMessage("W.ERROR.DELETE_PROJECT",
                        projectUpdate.getGitHubProject().getLocalProjectPath().toString()));
                throw ex;
            }
            mediamtxGitHub.downloadReleaseAndInstall(context, version, (progress, message, error) ->
                progressBar.progress(progress, message));
            Files.copy(projectUpdate.getGitHubProject().getLocalProjectPath().resolve("mediamtx.yml"),
                configurationPath, StandardCopyOption.REPLACE_EXISTING);
            syncConfiguration();
            // fire restart service
            initialize();
            return ActionResponseModel.success();
        }, null);
    }

    public void addSourceInfo(String path, Map<String, OptionModel> videoSources) {
        if (isMediaMtxAvailable(path)) {
            OptionModel webrtc = videoSources.get("webrtc");
            webrtc.addChild(OptionModel.of("mediamtx/video.webrtc", "MediaMTX WebRTC")
                                       .setIcon(new Icon("fab fa-stumbleupon-circle", "#22725A")));

            OptionModel hls = videoSources.get("hls");
            hls.addChild(OptionModel.of("mediamtx/index.m3u8", "MediaMTX HLS")
                                     .setIcon(FFMPEGFormat.HLS.getIconModel()));
        }
    }

    @Override
    protected void initialize() {
        isRunningLocally = getApiStatus() != 200;
        if (isRunningLocally) {
            startLocalProcess();
        } else {
            setStatusOnline();
        }
    }

    private int getApiStatus() {
        try {
            URL url = new URL(getApi("paths/list"));
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            return huc.getResponseCode();
        } catch (Exception ignore) {
        }
        return 500;
    }

    @SneakyThrows
    private void startLocalProcess() {
        log.info("Starting MediaMTX");
        // not need internet because we should fail create service if no internet
        Path executable = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx");
        if (!mediamtxGitHub.isLocalProjectInstalled()) {
            mediamtxGitHub.installLatestRelease(context);
            Files.copy(mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml"),
                configurationPath, StandardCopyOption.REPLACE_EXISTING);
            if (SystemUtils.IS_OS_LINUX) {
                context.hardware().execute("chmod +x " + executable);
            }
        }
        syncConfiguration();

        String processStr = executable + " " + configurationPath;

        AtomicReference<String> errorRef = new AtomicReference<>();
        this.processContext = context
            .bgp().processBuilder(getEntityID())
            .attachLogger(log)
            .attachEntityStatus(entity)
            .onStarted(this::setStatusOnline)
            .onFinished((ex, responseCode) -> {
                if (ex == null) {
                    if (errorRef.get() != null) {
                        ex = new ServerException(errorRef.get());
                    } else if (responseCode != 0) {
                        ex = new ServerException("Exit with code: " + responseCode);
                    }
                }
                dispose(ex);
            })
            .setInputLoggerOutput(msg -> {
                Level level = Level.DEBUG;
                if (msg.contains("WAR")) {
                    level = Level.WARN;
                } else if (msg.contains("ERR")) {
                    level = Level.ERROR;
                    if (msg.contains("cannot unmarshal")) {
                        errorRef.set("W.ERROR.MTX_CONFIG");
                    }
                }
                log.log(level, "[{}]: MediaMTX: {}", entityID, msg);
            })
            .execute(processStr);
    }

    private synchronized void scheduleRegisterSources() {
        if (pendingRegistrations.isEmpty() && !successRegistered.isEmpty() || !entity.getStatus().isOnline()) {
            return;
        }
        context.bgp().builder("MediaMTX-register-sources").delay(Duration.ofSeconds(10)).execute(() -> {
            // re-register all path if MediaMTX was restarted
                   /*for (Entry<String, MediaMTXSource> entry : successRegistered.entrySet()) {
                       registerPath(entry.getKey(), entry.getValue());
                   }*/
            if (!successRegistered.isEmpty()) {
                Set<String> nodes = new HashSet<>();
                for (JsonNode node : getApiList().path("items")) {
                    nodes.add(node.get("name").asText());
                }
                for (Iterator<Entry<String, String>> iterator = successRegistered.entrySet().iterator(); iterator.hasNext(); ) {
                    Entry<String, String> entry = iterator.next();
                    if (!nodes.contains(entry.getKey())) {
                        iterator.remove();
                        pendingRegistrations.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            for (Entry<String, String> entry : pendingRegistrations.entrySet()) {
                registerPath(entry.getKey(), entry.getValue());
            }
        });
    }

    @SneakyThrows
    private void syncConfiguration() {
        configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        boolean updated = false;

        if (!configuration.get("api").asText().equals("yes") && !configuration.get("api").asText().equals("true")) {
            configuration.put("api", true);
            updated = true;
        }

        updated |= checkPort(entity.getApiPort(), "apiAddress");
        updated |= checkPort(entity.getRtspPort(), "rtspAddress");
        updated |= checkPort(entity.getWebRtcPort(), "webrtcAddress");
        updated |= checkPort(entity.getHlsPort(), "hlsAddress");

        if (updated) {
            saveConfiguration();
        }
        apiURL = "http://" + configuration.get("apiAddress").asText();
    }

    private boolean checkPort(int port, String key) {
        String address = configuration.get(key).asText();
        if (!address.endsWith(":" + port)) {
            String value = address.substring(0, address.indexOf(":") + 1) + port;
            configuration.put(key, value);
            return true;
        }
        return false;
    }

    @SneakyThrows
    private void saveConfiguration() {
        try (OutputStream outputStream = Files.newOutputStream(configurationPath, StandardOpenOption.TRUNCATE_EXISTING)) {
            YAML_OBJECT_MAPPER.writeValue(outputStream, configuration);
        }
    }

    @Override
    @SneakyThrows
    public void backupData(BackupContext backupContext) {
        Files.copy(configurationPath, backupContext.getBackupPath().resolve(configurationPath.getFileName()));
    }

    private void registerPath(@NotNull String path, @NotNull String source) {
        String url = getApi("config/paths/add/%s".formatted(path));
        HttpRequest request = Curl.createPostRequest(url, new MediaMTXSource(source));
        Curl.sendAsync(request, Void.class, (unused, code) -> {
            if (code == 200) {
                log.info("[{}]: MediaMTX: Video source with path /{} successfully registered. Source: {}", entityID, path, source);
                pendingRegistrations.remove(path);
                successRegistered.put(path, source);
            } else {
                log.info("[{}]: MediaMTX: Unable to register path /{}. Source: {}. Code: {}", entityID, path, source, code);
            }
        });
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class MediaMTXSource {

        private final @NotNull String source;
        private boolean sourceOnDemand = false;
        private boolean sourceAnyPortEnable = false;
        private @NotNull SourceProtocol sourceProtocol = SourceProtocol.automatic;

        public enum SourceProtocol {
            automatic, udp, multicast, tcp
        }
    }
}

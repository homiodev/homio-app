package org.homio.app.service.video;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;
import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;
import static org.homio.app.model.entity.Go2RTCEntity.go2rtcGitHub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
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
import org.homio.app.model.entity.Go2RTCEntity;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Go2RTCService extends ServiceInstance<Go2RTCEntity>
    implements HasEntityIdentifier {

    private final Map<String, String> successRegistered = new ConcurrentHashMap<>();
    private final Map<String, String> pendingRegistrations = new ConcurrentHashMap<>();
    private final Path configurationPath;
    private @Getter String apiURL;
    private @Nullable UpdatableValue<JsonNode> pathData;

    private ProcessContext processContext;
    private ObjectNode configuration;

    private @Getter boolean isRunningLocally;

    public Go2RTCService(@NotNull Context context, @NotNull Go2RTCEntity entity) {
        super(context, entity, true);
        configurationPath = CommonUtils.getConfigPath().resolve("go2rtc.yaml");
        apiURL = "http://localhost:%d/api".formatted(entity.getApiPort());
        readConfiguration();
    }

    public JsonNode getApiList() {
        if (apiURL == null) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            if (pathData == null) {
                pathData = UpdatableValue.wrap(Curl.get(apiURL + "/streams", JsonNode.class), "paths");
            }
            return pathData.getFreshValue(Duration.ofSeconds(60), () ->
                Curl.sendAsync(Curl.createGetRequest(apiURL + "/streams"), JsonNode.class, (data, code) -> {
                    if (code == 200) {
                        pathData.update(data);
                    }
                }));
        } catch (Exception ignore) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    public void dispose(@Nullable Exception ex) {
        context.bgp().removeLowPriorityRequest("register-go2rtc");
        if (ex != null) {
            this.entity.setStatusError(ex);
        } else {
            if (entity.getStatus() != Status.ERROR) {
                this.entity.setStatus(Status.OFFLINE, entity.getStatusMessage());
            }
        }
        ContextBGP.cancel(processContext);
        log.warn("Dispose Go2RTC", ex);

        context.ui().toastr().warn("Dispose Go2RTC");
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
            HttpRequest request = Curl.createDeleteRequest("%s/streams&src=%s".formatted(apiURL, path));
            Curl.sendAsync(request, Void.class, (unused, code) -> {
                if (code == 200) {
                    log.error("[{}]: Go2RTC: Video source with path /{} successfully removed", entityID, path);
                } else {
                    log.error("[{}]: Go2RTC: Unable to remove video source with path /{}", entityID, path);
                }
            });
        }
    }

    @SneakyThrows
    public @NotNull ActionResponseModel updateFirmware(ProgressBar progressBar, String version) {
        return go2rtcGitHub.updateProject("go2rtc", progressBar, true, projectUpdate -> {
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
            go2rtcGitHub.downloadReleaseAndInstall(context, version, (progress, message, error) ->
                progressBar.progress(progress, message));
            resetConfiguration();
            // fire restart service
            initialize();
            return ActionResponseModel.success();
        }, null);
    }

    public void addSourceInfo(String path, Map<String, OptionModel> videoSources) {
        if (getApiListStreams().containsKey(path)) {
            OptionModel webrtc = videoSources.get("webrtc");
            webrtc.addChild(OptionModel.of("go2rtc/video.webrtc", "Go2RTC WebRTC")
                                       .setIcon(new Icon("fab fa-stumbleupon-circle", "#22725A")));

            OptionModel hls = videoSources.get("hls");
            hls.addChild(OptionModel.of("go2rtc/index.m3u8", "Go2RTC HLS")
                                    .setIcon(FFMPEGFormat.HLS.getIconModel()));
        }
    }

    @Override
    protected void initialize() {
        syncConfiguration();
        isRunningLocally = getApiStatus() != 200;
        if (isRunningLocally) {
            startLocalProcess();
        } else {
            setStatusOnline();
        }
    }

    private int getApiStatus() {
        try {
            URL url = new URL(apiURL);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            return huc.getResponseCode();
        } catch (Exception ignore) {
        }
        return 500;
    }

    private void startLocalProcess() {
        log.info("Starting Go2RTC");
        // not need internet because we should fail create service if no internet
        if (!go2rtcGitHub.isLocalProjectInstalled()) {
            go2rtcGitHub.installLatestRelease(context);
            if (SystemUtils.IS_OS_LINUX) {
                context.hardware().execute("chmod +x " + go2rtcGitHub.getLocalProjectPath().resolve("go2rtc_") + "*");
            }
        }

        String processStr = go2rtcGitHub.getLocalProjectPath().resolve("go2rtc")
            + " -config " + configurationPath;

        AtomicReference<String> errorRef = new AtomicReference<>();
        this.processContext = context
            .bgp().processBuilder(entity, log)
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
            .execute(processStr);
    }

    private void setStatusOnline() {
        entity.setStatusOnline();
        context.bgp().addLowPriorityRequest("register-go2rtc", this::scheduleRegisterSources);
    }

    private synchronized void scheduleRegisterSources() {
        if (pendingRegistrations.isEmpty() && !successRegistered.isEmpty() || !entity.getStatus().isOnline()) {
            return;
        }
        context.bgp().builder("Go2RTC-register-sources").delay(Duration.ofSeconds(10)).execute(() -> {
            if (!successRegistered.isEmpty()) {
                Set<String> nodes = getApiListStreams().keySet();
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

    private Map<String, JsonNode> getApiListStreams() {
        JsonNode list = getApiList();
        Map<String, JsonNode> nodes = new HashMap<>();
        list.fields().forEachRemaining(entry -> nodes.put(entry.getKey(), entry.getValue()));
        return nodes;
    }

    @SneakyThrows
    private void syncConfiguration() {
        readConfiguration();
        apiURL = "http://localhost:%d/api".formatted(entity.getApiPort());

        boolean updated = checkPort(entity.getApiPort(), "api");
        updated |= checkPort(entity.getRtspPort(), "rtsp");
        updated |= checkPort(entity.getWebRtcPort(), "webrtc");

        if (updated) {
            saveConfiguration();
        }
    }

    private boolean checkPort(int port, String key) {
        JsonNode node = configuration.get(key);
        String configListen = node.get("listen").asText();
        if (!configListen.endsWith(":" + port)) {
            String value = configListen.substring(0, configListen.indexOf(":") + 1) + port;
            ((ObjectNode) node).put("listen", value);
            return true;
        }
        return false;
    }

    @SneakyThrows
    public void resetConfiguration() {
        destroy(false, null);
        Files.delete(configurationPath);
        readConfiguration();
        long hashCode = entity.getEntityServiceHashCode();
        entity.setApiPort(1984);
        if (hashCode != entity.getEntityServiceHashCode()) {
            context.db().save(entity);
        }
        restartService();
    }

    @SneakyThrows
    private void saveConfiguration() {
        try (OutputStream outputStream = Files.newOutputStream(configurationPath, StandardOpenOption.TRUNCATE_EXISTING)) {
            YAML_OBJECT_MAPPER.writeValue(outputStream, configuration);
        }
    }

    private void registerPath(@NotNull String path, @NotNull String source) {
        HttpRequest request = Curl.createPutRequest("%s/streams?name=%s&src=%s".formatted(apiURL, path, source), null);
        Curl.sendAsync(request, String.class, (unused, code) -> {
            if (code == 200 || code == 400) { // somehow return status is 400
                log.info("[{}]: Go2RTC: Video source with path /{} successfully registered. Source: {}", entityID, path, source);
                pendingRegistrations.remove(path);
                successRegistered.put(path, source);
            } else {
                log.info("[{}]: Go2RTC: Unable to register path /{}. Source: {}. Code: {}", entityID, path, source, code);
            }
        });
    }

    private String buildDefaultConfiguration() {
        return DEFAULT_CONFIG.formatted(
            entity.getApiPort(),
            FFMPEG_LOCATION,
            entity.getRtspPort(),
            entity.getWebRtcPort());
    }

    @SneakyThrows
    private void readConfiguration() {
        if (!Files.exists(configurationPath)) {
            CommonUtils.writeToFile(configurationPath, buildDefaultConfiguration(), false);
        }
        try {
            configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        } catch (Exception ex) {
            log.error("[{}]: Unable to read {} configuration file", entityID, configurationPath, ex);
            CommonUtils.writeToFile(configurationPath, buildDefaultConfiguration(), false);
            configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        }
    }

    private static final String DEFAULT_CONFIG = """
        api:
          listen: ":%s"
          base_path: ""
          static_dir: ""
          origin: ""
                
        ffmpeg:
          bin: %s
          global: -hide_banner
          file: -re -stream_loop -1 -i {input}
          http: -fflags nobuffer -flags low_delay -i {input}
          rtsp: -fflags nobuffer -flags low_delay -timeout 5000000 -user_agent go2rtc/ffmpeg -rtsp_transport tcp -i {input}
          output: -user_agent ffmpeg/go2rtc -rtsp_transport tcp -f rtsp {output}
          # ... different presets for codecs
                
        hass:
          config: ""
                
        log:
          format: ""
          level: info
                
        ngrok:
          command: ""
                
        rtsp:
          listen: ":%s"
          username: ""
          password: ""
                
        srtp:
          listen: ":8443"
                
        streams: {}
                
        webrtc:
          listen: ":%s"
          candidates: []
          ice_servers:
            - urls: [ "stun:stun.l.google.com:19302" ]
              username: ""
              credential: ""
        """;
}

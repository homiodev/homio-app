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
import java.nio.file.Paths;
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
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ProcessContext;
import org.homio.api.EntityContextMedia.MediaMTXInfo;
import org.homio.api.EntityContextMedia.MediaMTXSource;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.UpdatableValue;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.ui.UI.Color;
import org.homio.api.util.Lang;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.model.entity.MediaMTXEntity.LogLevel;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class MediaMTXService extends ServiceInstance<MediaMTXEntity>
    implements HasEntityIdentifier {

    private final Map<String, MediaMTXSource> successRegistered = new ConcurrentHashMap<>();
    private final Map<String, MediaMTXSource> pendingRegistrations = new ConcurrentHashMap<>();
    private final Path configurationPath = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");
    private @Getter @Nullable String apiURL;
    private @Nullable UpdatableValue<JsonNode> pathData;

    private ProcessContext processContext;
    private ObjectNode configuration;

    private @Getter boolean isRunningLocally;

    public MediaMTXService(@NotNull EntityContext entityContext, @NotNull MediaMTXEntity entity) {
        super(entityContext, entity, true);
    }

    public JsonNode getApiList() {
        if (apiURL == null) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            if (pathData == null) {
                pathData = UpdatableValue.wrap(Curl.get(apiURL + "/v2/paths/list", JsonNode.class), "paths");
            }
            return pathData.getFreshValue(Duration.ofSeconds(60), () ->
                Curl.sendAsync(Curl.createGetRequest(apiURL + "/v2/paths/list"), JsonNode.class, (data, code) -> {
                    if (code == 200) {
                        pathData.update(data);
                    }
                }));
        } catch (Exception ignore) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    public void dispose(@Nullable Exception ex) {
        if (ex != null) {
            this.entity.setStatusError(ex);
        } else {
            if (entity.getStatus() != Status.ERROR) {
                this.entity.setStatus(Status.OFFLINE, entity.getStatusMessage());
            }
        }
        EntityContextBGP.cancel(processContext);
        log.warn("Dispose mediamtx", ex);

        entityContext.ui().sendWarningMessage("Dispose mediamtx");
    }

    @Override
    public void destroy() {
        this.dispose(null);
    }

    @SneakyThrows
    public void updateConfiguration(String mc) {
        configuration = YAML_OBJECT_MAPPER.readValue(mc, ObjectNode.class);
        saveConfiguration();
        initialize();
    }

    @SneakyThrows
    public void resetConfiguration() {
        Path backupFile = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx_initial.yml");
        if (!Files.exists(backupFile)) {
            throw new ServerException("W.ERROR.BACKUP_NOT_FOUND");
        }
        Files.copy(backupFile, configurationPath, StandardCopyOption.REPLACE_EXISTING);
        configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        configuration.put("api", true);
        long hashCode = entity.getEntityServiceHashCode();
        entity.setLogLevel(LogLevel.valueOf(configuration.get("logLevel").asText()));
        entity.setUdpMaxPayloadSize(configuration.get("udpMaxPayloadSize").asInt());
        entity.setReadBufferCount(configuration.get("readBufferCount").asInt());
        entity.setReadTimeout(intervalToInt("readTimeout"));
        entity.setWriteTimeout(intervalToInt("writeTimeout"));
        if (hashCode != entity.getEntityServiceHashCode()) {
            entityContext.save(entity);
        }
        saveConfiguration();
        restartService();
    }

    @Override
    public String isRequireRestartService() {
        if (!entity.isStart()) {return null;}
        if (!entity.getStatus().isOnline()) {return "Status: " + entity.getStatus();}
        int status = getApiStatus();
        if (status != 200) {return "API status[%s]".formatted(status);}
        return null;
    }

    public void addSource(@NotNull String path, MediaMTXSource source) {
        if (!successRegistered.containsKey(path) && !pendingRegistrations.containsKey(path)) {
            pendingRegistrations.put(path, source);
            if (entity.getStatus().isOnline()) {
                registerAllSources();
            }
        }
    }

    public void removeSource(String path) {
        pendingRegistrations.remove(path);
        MediaMTXSource source = successRegistered.remove(path);
        // if source was registered already
        if (source == null) {
            HttpRequest request = Curl.createPostRequest("%s/v2/config/paths/remove/%s".formatted(apiURL, path));
            Curl.sendAsync(request, Void.class, (unused, code) -> {
                if (code == 200) {
                    log.error("[{}]: MediaMTX: Video source with path /{} successfully removed", entityID, path);
                }
            });
        }
    }

    public MediaMTXInfo getMediaMTXInfo(String path) {
        for (JsonNode node : getApiList().get("items")) {
            if (node.get("name").asText().equals(path)) {
                return new MediaMTXInfoImpl(path, true, node.get("ready").asBoolean());
            }
        }
        return new MediaMTXInfoImpl(path, false, false);
    }

    @SneakyThrows
    public @NotNull ActionResponseModel updateFirmware(ProgressBar progressBar, String version) {
        return mediamtxGitHub.updateProject("mediamtx", progressBar, true, projectUpdate -> {
            dispose(null);
            Thread.sleep(1000); // wait to finish process. not sure if it's need

            try {
                projectUpdate.getGitHubProject().deleteProject();
            } catch (Exception ex) {
                getEntityContext().ui().sendErrorMessage(
                    Lang.getServerMessage("ZIGBEE.ERROR.DELETE",
                        projectUpdate.getGitHubProject().getLocalProjectPath().toString()));
                throw ex;
            }
            mediamtxGitHub.downloadReleaseAndInstall(entityContext, version, (progress, message, error) ->
                progressBar.progress(progress, message));
            if (projectUpdate.isHasBackup()) {
                projectUpdate.copyFromBackup(Set.of(Path.of("mediamtx_initial.yml"), Path.of("mediamtx.yml")));
            } else {
                mediamtxGitHub.backup(Paths.get("mediamtx.yml"), Paths.get("mediamtx_initial.yml"));
            }
            // fire restart service
            initialize();
            return ActionResponseModel.success();
        }, null);
    }

    @Override
    protected void initialize() {
        destroy();
        if (entity.isStart()) {
            syncConfiguration();
            isRunningLocally = getApiStatus() != 200;
            if (isRunningLocally) {
                startLocalProcess();
            } else {
                entity.setStatusOnline();
                registerAllSources();
            }
        }
    }

    private int getApiStatus() {
        try {
            URL url = new URL(apiURL + "/v2/paths/list");
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            return huc.getResponseCode();
        } catch (Exception ignore) {
        }
        return 500;
    }

    private void startLocalProcess() {
        log.info("Starting MediaMTX");
        // not need internet because we should fail create service if no internet
        if (!mediamtxGitHub.isLocalProjectInstalled()) {
            mediamtxGitHub.installLatestRelease(entityContext);
            mediamtxGitHub.backup(Paths.get("mediamtx.yml"), Paths.get("mediamtx_initial.yml"));
        }

        //updateNotificationBlock();

        String processStr = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx")
            + " " + mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");

        AtomicReference<String> errorRef = new AtomicReference<>();
        this.processContext = entityContext
            .bgp().processBuilder(getEntityID())
            .attachLogger(log)
            .attachEntityStatus(entity)
            .onStarted(() -> {
                registerAllSources();
                // re-register all path if MediaMTX was restarted
                for (Entry<String, MediaMTXSource> entry : successRegistered.entrySet()) {
                    registerPath(entry.getKey(), entry.getValue());
                }
            })
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
                log.log(level, "[{}]: MediaMTX: {}", getEntity(), msg);
            })
            .execute(processStr);
    }

    /*private void updateNotificationBlock() {
        entityContext.ui().addNotificationBlock(entityID, "MediaMTX", new Icon("fas fa-square-rss", "#308BB3"), builder -> {
            builder.setStatus(entity.getStatus());
            builder.linkToEntity(entity);
            builder.setUpdatable(entity);

            if (entity.getStatus().isOnline()) {
                builder.addInfo("ACTION.SUCCESS", new Icon("fas fa-seedling", Color.GREEN));
            } else {
                builder.addErrorStatusInfo(entity.getStatusMessage());
            }
        });
    }*/

    @SneakyThrows
    private void syncConfiguration() {
        configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        apiURL = "http://" + configuration.get("apiAddress").asText();

        boolean updated = false;

        if (!configuration.get("api").asText().equals("yes") && !configuration.get("api").asText().equals("true")) {
            configuration.put("api", true);
            updated = true;
        }

        if (!configuration.get("logLevel").asText().equals(entity.getLogLevel().name())) {
            configuration.put("logLevel", entity.getLogLevel().name());
            log.debug("Update logLevel config");
            updated = true;
        }
        if (configuration.get("udpMaxPayloadSize").asInt() != entity.getUdpMaxPayloadSize()) {
            configuration.put("udpMaxPayloadSize", entity.getUdpMaxPayloadSize());
            log.debug("Update udpMaxPayloadSize config");
            updated = true;
        }
        if (configuration.get("readBufferCount").asInt() != entity.getReadBufferCount()) {
            configuration.put("readBufferCount", entity.getReadBufferCount());
            log.debug("Update readBufferCount config");
            updated = true;
        }
        if (!configuration.get("readTimeout").asText().equals(entity.getReadTimeout() + "s")) {
            configuration.put("readTimeout", entity.getReadTimeout() + "s");
            log.debug("Update readTimeout config");
            updated = true;
        }
        if (!configuration.get("writeTimeout").asText().equals(entity.getReadTimeout() + "s")) {
            configuration.put("writeTimeout", entity.getReadTimeout() + "s");
            log.debug("Update writeTimeout config");
            updated = true;
        }
        if (updated) {
            saveConfiguration();
        }
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

    private void registerAllSources() {
        if (!successRegistered.isEmpty()) {
            Set<String> nodes = new HashSet<>();
            for (JsonNode node : getApiList().path("items")) {
                nodes.add(node.get("name").asText());
            }
            for (Iterator<Entry<String, MediaMTXSource>> iterator = successRegistered.entrySet().iterator(); iterator.hasNext(); ) {
                Entry<String, MediaMTXSource> entry = iterator.next();
                if (!nodes.contains(entry.getKey())) {
                    iterator.remove();
                    pendingRegistrations.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Entry<String, MediaMTXSource> entry : pendingRegistrations.entrySet()) {
            registerPath(entry.getKey(), entry.getValue());
        }
    }

    private void registerPath(@NotNull String path, @NotNull MediaMTXSource source) {
        HttpRequest request = Curl.createPostRequest("%s/v2/config/paths/add/%s".formatted(apiURL, path), source);
        Curl.sendAsync(request, Void.class, (unused, code) -> {
            if (code == 200) {
                log.info("[{}]: MediaMTX: Video source with path /{} successfully registered. Source: {}", entityID, path, source.getSource());
                pendingRegistrations.remove(path);
                successRegistered.put(path, source);
            } else {
                log.info("[{}]: MediaMTX: Unable to register path /{}. Source: {}. Code: {}", entityID, path, source.getSource(), code);
            }
        });
    }

    @Getter
    @RequiredArgsConstructor
    private static class MediaMTXInfoImpl implements MediaMTXInfo {

        private final String path;
        private final boolean exists;
        private final boolean ready;
    }

    private int intervalToInt(String key) {
        return (int) Duration.parse("PT" + configuration.get(key).asText()).getSeconds();
    }
}

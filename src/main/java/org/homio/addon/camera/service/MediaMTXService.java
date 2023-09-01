package org.homio.addon.camera.service;

import static org.homio.addon.camera.entity.MediaMTXEntity.mediamtxGitHub;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.api.util.JsonUtils.YAML_OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Null;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.addon.camera.entity.MediaMTXEntity;
import org.homio.addon.camera.entity.MediaMTXEntity.LogLevel;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ProcessContext;
import org.homio.api.exception.ServerException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class MediaMTXService extends ServiceInstance<MediaMTXEntity>
    implements HasEntityIdentifier {

    private final Path configurationPath = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");
    private @Getter @Null String apiURL;

    private ProcessContext processContext;
    private ObjectNode configuration;
    @Getter
    private boolean isRunningLocally;

    public MediaMTXService(@NotNull EntityContext entityContext, @NotNull MediaMTXEntity entity) {
        super(entityContext, entity, true);
    }

    public JsonNode getApiList() {
        if (apiURL != null) {
            try {
                return Curl.get(apiURL + "/v2/paths/list", JsonNode.class);
            } catch (Exception ex) {
                log.warn("Unable to fetch api list: {}", getErrorMessage(ex));
            }
        }
        return OBJECT_MAPPER.createObjectNode();
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
        configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        configuration.put("api", true);
        long hashCode = entity.getEntityServiceHashCode();
        entity.setLogLevel(LogLevel.valueOf(configuration.get("logLevel").asText()));
        entity.setUdpMaxPayloadSize(configuration.get("udpMaxPayloadSize").asInt());
        entity.setReadBufferCount(configuration.get("readBufferCount").asInt());
        entity.setReadTimeout(configuration.get("readTimeout").asInt());
        entity.setWriteTimeout(configuration.get("writeTimeout").asInt());
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
        EntityContextBGP.cancel(processContext);

        // not need internet because we should fail create service if no internet
        if (!mediamtxGitHub.isLocalProjectInstalled()) {
            mediamtxGitHub.installLatestRelease(entityContext);
            mediamtxGitHub.backup(Paths.get("mediamtx.yml"), Paths.get("mediamtx_initial.yml"));
        }

        String processStr = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx")
            + " " + mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");

        processContext = entityContext.bgp().processBuilder(getEntityID())
                                      .attachLogger(log)
                                      .attachEntityStatus(entity)
                                      .execute(processStr);

        AtomicReference<String> errorRef = new AtomicReference<>();
        this.processContext = entityContext
            .bgp().processBuilder(getEntityID())
            .onStarted(t -> entity.setStatusOnline())
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
                Level level = Level.INFO;
                if (msg.contains("WAR")) {
                    level = Level.WARN;
                } else if (msg.contains("ERR")) {
                    level = Level.ERROR;
                    if (msg.contains("cannot unmarshal")) {
                        errorRef.set("W.ERROR.MTX_CONFIG");
                    }
                }
                logService(msg, level);
            })
            .execute(processStr);
    }

    private void logService(String msg, Level level) {
        log.log(level, "MediaMTX: {}", msg);
    }

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
}

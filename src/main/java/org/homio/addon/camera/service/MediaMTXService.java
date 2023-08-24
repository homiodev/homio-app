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
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.addon.camera.entity.MediaMTXEntity;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ProcessContext;
import org.homio.api.exception.ServerException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.service.EntityService.WatchdogService;
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

    @Override
    public WatchdogService getWatchdog() {
        return new WatchdogService() {
            @Override
            public void restartService() {
                initialize();
            }

            @Override
            public boolean isRequireRestartService() {
                return entity.isStart() && entity.getStatus().isOnline() && !isServiceRunning();
            }
        };
    }

    public JsonNode getApiList() {
        if (apiURL != null) {
            try {
                return Curl.get(apiURL, JsonNode.class);
            } catch (Exception ex) {
                log.warn("[{}]: Unable to fetch api list: {}", entityID, getErrorMessage(ex));
            }
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    public void dispose(@Nullable Exception ex) {
        if (ex != null) {
            this.entity.setStatusError(ex);
        } else {
            this.entity.setStatus(Status.OFFLINE, null);
        }
        if (processContext != null) {
            processContext.cancel(true);
            processContext = null;
        }

        log.warn("[{}]: Dispose mediamtx", entityID, ex);

        entityContext.ui().sendWarningMessage("Dispose mediamtx");
    }

    @Override
    public void destroy() {
        this.dispose(null);
    }

    @Override
    protected void initialize() {
        destroy();
        if (entity.isStart()) {
            syncConfiguration();
            isRunningLocally = !isServiceRunning();
            if (isRunningLocally) {
                startLocalProcess();
            } else {
                entity.setStatusOnline();
            }
        }
    }

    private boolean isServiceRunning() {
        try {
            URL url = new URL(apiURL);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("HEAD");
            if (HttpURLConnection.HTTP_OK == huc.getResponseCode()) {
                return true;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    private void startLocalProcess() {
        String processStr = mediamtxGitHub.getLocalProjectPath().resolve("mediamtx")
            + " " + mediamtxGitHub.getLocalProjectPath().resolve("mediamtx.yml");
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
                if (ex != null) {
                    log.error("[{}]: Error while start mediamtx {}", entityID, getErrorMessage(ex));
                } else {
                    log.warn("[{}]: mediamtx finished with status: {}", entityID, responseCode);
                }
                processContext = null;
                dispose(ex);
            })
            .setErrorLoggerOutput(message -> logService(message, Level.ERROR))
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
        log.log(level, "[{}]: mediamtx: {}", entityID, msg);
    }

    @SneakyThrows
    private void syncConfiguration() {
        configuration = YAML_OBJECT_MAPPER.readValue(configurationPath.toFile(), ObjectNode.class);
        apiURL = configuration.get("apiAddress").asText();

        boolean updated = false;

        if (!configuration.get("api").asText().equals("yes") && !configuration.get("api").asText().equals("true")) {
            configuration.put("api", true);
            updated = true;
        }

        if (!configuration.get("logLevel").asText().equals(entity.getLogLevel().name())) {
            configuration.put("logLevel", entity.getLogLevel().name());
            updated = true;
        }
        if (configuration.get("udpMaxPayloadSize").asInt() != entity.getUdpMaxPayloadSize()) {
            configuration.put("udpMaxPayloadSize", entity.getUdpMaxPayloadSize());
            updated = true;
        }
        if (configuration.get("readBufferCount").asInt() != entity.getReadBufferCount()) {
            configuration.put("readBufferCount", entity.getReadBufferCount());
            updated = true;
        }
        if (!configuration.get("readTimeout").asText().equals(entity.getReadTimeout() + "s")) {
            configuration.put("readTimeout", entity.getReadTimeout() + "s");
            updated = true;
        }
        if (!configuration.get("writeTimeout").asText().equals(entity.getReadTimeout() + "s")) {
            configuration.put("writeTimeout", entity.getReadTimeout() + "s");
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
}

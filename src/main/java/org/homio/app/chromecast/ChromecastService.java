package org.homio.app.chromecast;

import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.litvak.chromecast.api.v2.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;

public class ChromecastService extends EntityService.ServiceInstance<ChromecastEntity> implements
  ChromeCastSpontaneousEventListener, ChromeCastConnectionEventListener {
  public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
    new ConfigDeviceDefinitionService("chromecast-devices.json");

  public static String MEDIA_PLAYER = "CC1AD845";

  @Getter
  Map<String, ChromecastEndpoint> endpoints = new ConcurrentHashMap<>();

  private ChromeCast chromeCast;

  @Getter
  private ChromecastPlayer streamPlayer;

  public ChromecastService(@NotNull Context context, @NotNull ChromecastEntity entity) {
    super(context, entity, false, "chromecast");

    addEndpoint("imageSrc", () -> null, ep -> ep.setMediaStatusReader(status ->
      getMetadata(status, "images").map(metadata -> {
        //noinspection unchecked
        List<Map<String, String>> strings = (List<Map<String, String>>) metadata;
        for (Map<String, String> stringMap : strings) {
          String url = stringMap.get("url");
          if (url != null) {
            return new StringType(url);
          }
        }
        return null;
      }).orElse(null)));

    for (Map.Entry<String, ConfigDeviceEndpoint> ep : CONFIG_DEVICE_SERVICE.getDeviceEndpoints().entrySet()) {
      addEndpoint(ep.getKey(), () ->
        switch (ep.getKey()) {
          case "play" -> (ThrowingConsumer<State, Exception>) state -> {
            if (state.boolValue()) {
              handlePlay();
            } else {
              handlePause();
            }
          };
          case "next" -> state -> handleNextTrack();
          case "stop" -> state -> closeApp(null);
          case "mute" ->
            state -> sendCommand(entity, () -> chromeCast.setMuted(state.boolValue()), "Mute/unmute volume");
          case "playuri" -> state -> playMedia(null, state.stringValue(), null);
          case "volume" ->
            state -> sendCommand(entity, () -> chromeCast.setVolumeByIncrement(state.floatValue() / 100), "Set volume");
          default -> null;
        }, cep -> {
        switch (ep.getKey()) {
          case "volume" -> cep.setStatusReader(status -> new DecimalType(status.volume.level * 100));
          case "mute" -> cep.setStatusReader(status -> OnOffType.of(status.volume.muted));
          case "duration" ->
            cep.setMediaStatusReader(status -> State.of(ofNullable(status.media).map(m -> m.duration).orElse(null)));
          case "currentTime" -> cep.setMediaStatusReader(status -> new DecimalType(status.currentTime));
          case "idling" ->
            cep.setStatusReader(status -> OnOffType.of(ofNullable(status.getRunningApp()).map(a -> a.isIdleScreen).orElse(false)));
          case "statusText" ->
            cep.setStatusReader(status -> new StringType(ofNullable(status.getRunningApp()).map(a -> a.statusText).orElse("N/A")));
          case "appId" ->
            cep.setStatusReader(status -> new StringType(ofNullable(status.getRunningApp()).map(a -> a.id).orElse("N/A")));
          case "appName" ->
            cep.setStatusReader(status -> new StringType(ofNullable(status.getRunningApp()).map(a -> a.name).orElse("N/A")));
          case "play" -> cep.setMediaStatusReader(status -> OnOffType.of(isPlaying(status)));
          default -> {
            if (ep.getValue().getMetadata().optBoolean("readMeta")) {
              cep.setMediaStatusReader(status -> getMetadata(status, ep.getKey()).map(State::of).orElse(null));
            }
          }
        }
      });
    }
  }

  private Optional<Object> getMetadata(MediaStatus status, String field) {
    if (status.media != null && status.media.metadata != null && status.media.metadata.containsKey(field)) {
      return Optional.ofNullable(status.media.metadata.get(field));
    }
    return Optional.empty();
  }

  private boolean isPlaying(MediaStatus status) {
    switch (status.playerState) {
      case BUFFERING, LOADING, PLAYING -> {
        return false;
      }
    }
    return true;
  }

  @SneakyThrows
  void handlePause() {
    sendCommand(entity, () -> {
      Application app = chromeCast.getRunningApp();
      if (app != null) {
        MediaStatus mediaStatus = chromeCast.getMediaStatus();
        if (mediaStatus != null && mediaStatus.playerState != MediaStatus.PlayerState.IDLE
            && ((mediaStatus.supportedMediaCommands & 0x00000001) == 0x1)) {
          chromeCast.pause();
        }
      }
    }, "Pause");
  }

  @SneakyThrows
  void handlePlay() {
    sendCommand(entity, () -> {
      Application app = chromeCast.getRunningApp();
      if (app != null) {
        MediaStatus mediaStatus = chromeCast.getMediaStatus();
        if (mediaStatus != null && mediaStatus.playerState != MediaStatus.PlayerState.IDLE) {
          chromeCast.play();
        }
      }
    }, "Play");
  }

  @SneakyThrows
  private void handleNextTrack() {
    int duration = endpoints.get("duration").getValue().intValue();
    if (duration > 0) {
      sendCommand(entity, () -> chromeCast.seek(duration - 5), "Next track");
    }
  }

  public void playMedia(@Nullable String title, @Nullable String url, @Nullable String mimeType) {
    startApp(MEDIA_PLAYER);
    try {
      if (url != null && chromeCast.isAppRunning(MEDIA_PLAYER)) {
        // If the current track is paused, launching a new request results in nothing happening, therefore
        // resume current track.
        MediaStatus ms = chromeCast.getMediaStatus();
        if (ms != null && MediaStatus.PlayerState.PAUSED == ms.playerState && url.equals(ms.media.url)) {
          log.debug("Current stream paused, resuming");
          chromeCast.play();
        } else {
          chromeCast.load(title, null, url, mimeType);
        }
      } else {
        log.warn("Missing media player app - cannot process media.");
      }
      entity.setStatusOnline();
    } catch (IOException ex) {
      if ("Unable to load media".equals(ex.getMessage())) {
        log.warn("Unable to load media: {}", url);
      } else {
        log.debug("Failed playing media: {}", ex.getMessage());
        entity.setStatusError(ex);
      }
    }
  }

  @SneakyThrows
  public void startApp(@Nullable String appId) {
    if (appId == null) {
      return;
    }
    try {
      if (chromeCast.isAppAvailable(appId)) {
        if (!chromeCast.isAppRunning(appId)) {
          chromeCast.launchApp(appId);
          log.debug("Application launched: {}", appId);
        }
      } else {
        log.warn("Failed starting app, app probably not installed. Appid: {}", appId);
      }
      entity.setStatusOnline();
    } catch (IOException e) {
      closeApp(appId);
      chromeCast.launchApp(appId);
    }
  }

  public void closeApp(@Nullable String appId) {
    if (appId == null) {
      sendCommand(entity, () -> {
        Application app = chromeCast.getRunningApp();
        if (app != null) {
          chromeCast.stopApp();
        }
      }, "Close app");
      return;
    }
    sendCommand(entity, () -> {
      if (chromeCast.isAppRunning(appId)) {
        Application app = chromeCast.getRunningApp();
        if (app.id.equals(appId)) {
          chromeCast.stopApp();
        }
      }
    }, "Stopping app: " + appId);
  }

  private void sendCommand(@NotNull ChromecastEntity entity, ThrowingRunnable<Exception> handler, String error) {
    try {
      handler.run();
      entity.setStatus(Status.ONLINE);
    } catch (Exception ex) {
      log.warn("{} failed: {}", error, ex.getMessage());
      entity.setStatusError(ex);
    }
  }

  private void addEndpoint(
    @NotNull String endpointId,
    @NotNull Supplier<ThrowingConsumer<State, Exception>> updateHandlerGetter,
    @NotNull Consumer<ChromecastEndpoint> configuration) {
    endpoints.computeIfAbsent(endpointId, key -> {
      ChromecastEndpoint endpoint = new ChromecastEndpoint(context);
      endpoint.setUpdateHandler(updateHandlerGetter.get());
      configuration.accept(endpoint);
      endpoint.init(CONFIG_DEVICE_SERVICE, key, entity, key, null);
      return endpoint;
    });
  }

  @Override
  public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
    context.media().removeAudioPlayer(streamPlayer);
  }

  @Override
  protected void initialize() {
    context.event().addEntityStatusUpdateListener(entityID, "chromecast-service", e ->
      endpoints.get(ENDPOINT_DEVICE_STATUS).setValue(State.of(entity.getStatus()), true));

    chromeCast = new ChromeCast(entity.getHost(), entity.getPort());
    chromeCast.registerListener(this);
    chromeCast.registerConnectionListener(this);

    connect();
  }

  private void connect() {
    try {
      chromeCast.connect();
      streamPlayer = new ChromecastPlayer(this);
      context.media().addAudioPlayer(streamPlayer);
      entity.setStatusOnline();
    } catch (final IOException | GeneralSecurityException ex) {
      log.debug("Connect failed, trying to reconnect: {}", ex.getMessage());
      entity.setStatusError(ex);
      scheduleConnect();
    }
  }

  public synchronized void scheduleConnect() {
    log.debug("Scheduling connection");
    context.bgp().builder("chromecast-connect-" + entityID)
      .delay(Duration.ofSeconds(10))
      .execute(this::connect);
  }

  public synchronized void scheduleRefresh() {
    log.debug("Scheduling refresh");
    context.bgp().builder("chromecast-connect-" + entityID)
      .delay(Duration.ofSeconds(1))
      .delay(Duration.ofSeconds(entity.getRefreshRate()))
      .execute(this::connect);
  }

  @Override
  public void spontaneousEventReceived(ChromeCastSpontaneousEvent event) {
    log.trace("Received an {} event (class={})", event.getType(), event.getData());
    switch (event.getType()) {
      case CLOSE:
        for (ChromecastEndpoint endpoint : endpoints.values()) {
          endpoint.fireCloseEvent();
        }
        break;
      case MEDIA_STATUS:
        entity.setStatusOnline();
        MediaStatus mediaStatus = event.getData(MediaStatus.class);
        for (ChromecastEndpoint endpoint : endpoints.values()) {
          endpoint.fireEvent(mediaStatus);
        }
        break;
      case STATUS:
        entity.setStatusOnline();
        var status = event.getData(su.litvak.chromecast.api.v2.Status.class);
        for (ChromecastEndpoint endpoint : endpoints.values()) {
          endpoint.fireEvent(status);
        }
        break;
    }
  }

  @Override
  public void connectionEventReceived(ChromeCastConnectionEvent event) {
    if (event.isConnected()) {
      entity.setStatusOnline();
      scheduleRefresh();
    } else {
      entity.setStatus(Status.OFFLINE);
      scheduleConnect();
    }
  }

  public boolean isPlaying() {
    try {
      return chromeCast.isAppRunning(MEDIA_PLAYER);
    } catch (Exception ignored) {
      return false;
    }
  }
}

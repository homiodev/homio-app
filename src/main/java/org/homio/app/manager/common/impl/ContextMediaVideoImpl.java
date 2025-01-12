package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextMediaVideo;
import org.homio.api.Unregistered;
import org.homio.api.model.OptionModel;
import org.homio.api.stream.video.VideoPlayer;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;

@Log4j2
@RequiredArgsConstructor
public class ContextMediaVideoImpl implements ContextMediaVideo {

  @Getter
  private final Map<String, VideoPlayer> videoPlayers = new HashMap<>();
  @Getter
  @Accessors(fluent = true)
  private final Context context;
  private final FfmpegHardwareRepository repo;

  public static final Map<String, Integer> webRTCProviders = new HashMap<>();
  public static final Map<String, Integer> hlsProviders = new HashMap<>();

  private final Map<String, ContextMediaVideo.RegisterVideoSourceListener> registerVideoSourceListeners = new HashMap<>();
  private final Map<String, ContextMediaVideo.VideoSourceInfoListener> registerVideoSourceInfoListeners = new HashMap<>();

  @Override
  public Unregistered addVideoSourceInfo(@NotNull String path, @NotNull Map<String, OptionModel> videoSources) {
    for (VideoSourceInfoListener listener : registerVideoSourceInfoListeners.values()) {
      listener.addVideoSourceInfo(path, videoSources);
    }
    return () -> {
      for (VideoSourceInfoListener listener : registerVideoSourceInfoListeners.values()) {
        listener.removeVideoSourceInfo(path);
      }
    };
  }

  @Override
  public Unregistered addVideoSourceInfoListener(@NotNull String name, @NotNull VideoSourceInfoListener listener) {
    registerVideoSourceInfoListeners.put(name, listener);
    return () -> registerVideoSourceInfoListeners.remove(name);
  }

  @Override
  public Unregistered addVideoWebRTCProvider(@NotNull String name, int port) {
    webRTCProviders.put(name, port);
    return () -> webRTCProviders.remove(name);
  }

  @Override
  public Unregistered addVideoHLSProvider(@NotNull String name, int port) {
    hlsProviders.put(name, port);
    return () -> hlsProviders.remove(name);
  }

  @Override
  public Unregistered addVideoSource(@NotNull String path, @NotNull String source) {
    for (RegisterVideoSourceListener listener : registerVideoSourceListeners.values()) {
      listener.addVideoSource(path, source);
    }
    return () -> {
      for (RegisterVideoSourceListener listener : registerVideoSourceListeners.values()) {
        listener.removeVideoSource(path);
      }
    };
  }

  @Override
  public Unregistered addVideoSourceListener(@NotNull String key, @NotNull RegisterVideoSourceListener listener) {
    registerVideoSourceListeners.put(key, listener);
    return () -> registerVideoSourceListeners.remove(key);
  }

  @Override
  public @NotNull VideoInputDevice createVideoInputDevice(@NotNull String vfile) {
    return repo.createVideoInputDevice(FFMPEG_LOCATION, vfile);
  }

  @Override
  public Unregistered addVideoPlayer(@NotNull VideoPlayer player) {
    videoPlayers.put(player.getId(), player);
    return () -> videoPlayers.remove(player.getId());
  }

  @Override
  public @NotNull Set<String> getVideoDevices() {
    return repo.getVideoDevices(FFMPEG_LOCATION);
  }
}

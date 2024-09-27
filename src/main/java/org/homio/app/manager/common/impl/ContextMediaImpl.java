package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.ContextBGP;
import org.homio.api.ContextMedia;
import org.homio.api.ContextUI;
import org.homio.api.model.OptionModel;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.StreamPlayer;
import org.homio.api.stream.audio.AudioInput;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.api.stream.video.VideoPlayer;
import org.homio.api.util.CommonUtils;
import org.homio.app.audio.BuildInMicrophoneInput;
import org.homio.app.audio.JavaSoundAudioPlayer;
import org.homio.app.audio.WebPlayer;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.FirefoxWebDriverEntity;
import org.homio.app.model.entity.Go2RTCEntity;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.rest.MediaController;
import org.homio.app.video.ffmpeg.FFMPEGImpl;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Port;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@RequiredArgsConstructor
public class ContextMediaImpl implements ContextMedia {

    private final @Getter
    @Accessors(fluent = true) ContextImpl context;

    private final FfmpegHardwareRepository repo;

    public static String FFMPEG_LOCATION = SystemUtils.IS_OS_LINUX ? "ffmpeg" :
            CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe").toString();

    @Getter
    private final Map<String, AudioPlayer> audioPlayers = new HashMap<>();
    @Getter
    private final Map<String, VideoPlayer> videoPlayers = new HashMap<>();
    @Getter
    private final Map<String, AudioInput> audioInputs = new HashMap<>();

    @Override
    public void fireSeleniumFirefox(@NotNull ThrowingConsumer<WebDriver, Exception> driverHandler) {
        FirefoxWebDriverEntity.executeInWebDriver(driverHandler);
    }

    @Override
    public void fireSeleniumFirefox(@NotNull String title, @NotNull String icon, @NotNull String iconColor,
                                    @NotNull ThrowingConsumer<WebDriver, Exception> driverHandler) {
        FirefoxWebDriverEntity.executeInWebDriver(driver -> {
            AtomicReference<String> imageRef = new AtomicReference<>("");
            ContextUI.ContextUIDialog.MirrorImageDialog imageDialog = context().ui().dialog().topImageDialog(title, icon, iconColor);
            AtomicInteger passedSeconds = new AtomicInteger(0);
            AtomicBoolean closed = new AtomicBoolean(false);
            ContextBGP.ThreadContext<Void> screenShotFetcher = context().bgp().builder("run-driver-screenshot-fetcher")
                    .delay(Duration.ofSeconds(5))
                    .interval(Duration.ofSeconds(1))
                    .cancelOnError(false)
                    .execute(() -> {
                        if (!closed.get()) {
                            try {
                                String image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
                                if (!imageRef.get().equals(image) || passedSeconds.getAndIncrement() == 10) {
                                    passedSeconds.set(0);
                                    imageRef.set(image);
                                    imageDialog.sendImage(image);
                                }
                            } catch (Exception ex) {
                                log.error("Error fetch firefox image driver: {}", ex.getMessage());
                            }
                        }
                    });
            try {
                driverHandler.accept(driver);
            } finally {
                closed.set(true);
                ContextBGP.cancel(screenShotFetcher);
                imageDialog.sendImage(null);
            }
        });
    }

    @Override
    public void fireFfmpeg(@NotNull String inputOptions, @NotNull String source, @NotNull String output, int maxWaitTimeout) {
        repo.fireFfmpeg(FFMPEG_LOCATION, inputOptions, source, output, maxWaitTimeout);
    }

    @Override
    public void addVideoSourceInfo(@NotNull String path, @NotNull Map<String, OptionModel> videoSources) {
        MediaMTXEntity.getEntity(context).getService().addSourceInfo(path, videoSources);
        Go2RTCEntity.getEntity(context).getService().addSourceInfo(path, videoSources);
    }

    @Override
    public void registerVideoSource(@NotNull String path, @NotNull String source) {
        MediaMTXEntity.getEntity(context).getService().addSource(path, source);
        Go2RTCEntity.getEntity(context).getService().addSource(path, source);
    }

    @Override
    public void unRegisterVideoSource(@NotNull String path) {
        MediaMTXEntity.getEntity(context).getService().removeSource(path);
        Go2RTCEntity.getEntity(context).getService().removeSource(path);
    }

    @Override
    public @NotNull VideoInputDevice createVideoInputDevice(@NotNull String vfile) {
        return repo.createVideoInputDevice(FFMPEG_LOCATION, vfile);
    }

    @Override
    public void addAudioPlayer(@NotNull AudioPlayer player) {
        audioPlayers.put(player.getId(), player);
    }

    public void addStreamPlayer(@NotNull StreamPlayer player) {
        if (player instanceof VideoPlayer videoPlayer) {
            videoPlayers.put(player.getId(), videoPlayer);
        }
        if (player instanceof AudioPlayer audioPlayer) {
            audioPlayers.put(player.getId(), audioPlayer);
        }
    }

    @Override
    public void removeAudioPlayer(@NotNull AudioPlayer player) {
        audioPlayers.remove(player.getId());
    }

    @Override
    public void addVideoPlayer(@NotNull VideoPlayer player) {
        videoPlayers.put(player.getId(), player);
    }

    @Override
    public void removeVideoPlayer(@NotNull VideoPlayer player) {
        videoPlayers.remove(player.getId());
    }

    @Override
    public void addAudioInput(@NotNull AudioInput input) {
        audioInputs.put(input.getId(), input);
    }

    @Override
    public void removeAudioInput(@NotNull AudioInput input) {
        audioInputs.remove(input.getId());
    }

    @Override
    public @NotNull String createStreamUrl(@NotNull ContentStream stream, int timeoutOnInactiveSeconds) {
        return context.getBean(MediaController.class).createStreamUrl(stream, timeoutOnInactiveSeconds);
    }

    @Override
    public @NotNull Set<String> getVideoDevices() {
        return repo.getVideoDevices(FFMPEG_LOCATION);
    }

    @Override
    public @NotNull Set<String> getAudioDevices() {
        return repo.getAudioDevices(FFMPEG_LOCATION);
    }

    @Override
    public @NotNull FFMPEG buildFFMPEG(
            @NotNull String entityID, @NotNull String description,
            @NotNull FFMPEGHandler handler,
            @NotNull FFMPEGFormat format,
            @NotNull String inputArguments,
            @NotNull String input,
            @NotNull String outArguments,
            @NotNull String output,
            @NotNull String username,
            @NotNull String password) {
        return new FFMPEGImpl(entityID, description, handler, format, inputArguments, input, outArguments, output, username, password, context);
    }

    public void onContextCreated() {
        addAudioInput(new BuildInMicrophoneInput());
        addStreamPlayer(new WebPlayer(context));
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(Port.Info.SPEAKER)) {
                addAudioPlayer(new JavaSoundAudioPlayer(context, mixer));
            }
        }
    }

    public AudioPlayer getWebAudioPlayer() {
        return audioPlayers.get(WebPlayer.ID);
    }

    public @Nullable StreamPlayer getPlayer(String speakerId) {
        StreamPlayer player = audioPlayers.get(speakerId);
        if (player == null) {
            return videoPlayers.get(speakerId);
        }
        return player;
    }
}

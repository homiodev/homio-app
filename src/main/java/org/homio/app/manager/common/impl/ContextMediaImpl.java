package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingConsumer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.ContextBGP;
import org.homio.api.ContextMedia;
import org.homio.api.ContextUI;
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
import org.homio.app.rest.MediaController;
import org.homio.app.video.ffmpeg.FFMPEGImpl;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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

  public static String FFMPEG_LOCATION = SystemUtils.IS_OS_LINUX ? "ffmpeg" :
    CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe").toString();
  private final @Getter
  @Accessors(fluent = true) ContextImpl context;
  private final FfmpegHardwareRepository repo;
  @Getter
  private final Map<String, AudioPlayer> audioPlayers = new HashMap<>();
  @Getter
  private final Map<String, VideoPlayer> videoPlayers = new HashMap<>();
  @Getter
  private final Map<String, AudioInput> audioInputs = new HashMap<>();

  private final Map<String, WebDriverContext> webDriverInteracts = new HashMap<>();

  private final ContextMediaVideoImpl video;

  @Override
  public @NotNull ContextMediaVideoImpl video() {
    return video;
  }

  @Override
  public boolean isWebDriverAvailable() {
    return FirefoxWebDriverEntity.isWebDriverAvailable();
  }

  @Override
  public void fireSelenium(@NotNull ThrowingConsumer<WebDriver, Exception> driverHandler) {
    FirefoxWebDriverEntity.executeInWebDriver(driverHandler);
  }

  @Override
  public void fireSelenium(@NotNull String title, @NotNull String icon, @NotNull String iconColor,
                           @NotNull ThrowingConsumer<WebDriver, Exception> driverHandler) {
    FirefoxWebDriverEntity.executeInWebDriver(driver -> {
      String uuid = CommonUtils.generateShortUUID(8);
      webDriverInteracts.put(uuid, new WebDriverContext(driver));

      AtomicReference<String> imageRef = new AtomicReference<>("");
      ContextUI.ContextUIDialog.MirrorImageDialog imageDialog = context().ui().dialog().buildMirrorDialog(title, icon, iconColor, jsonNodes ->
        jsonNodes.put("webdriver", uuid));
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
                if (closed.get()) {
                  return;
                }
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
  public @NonNull String getFfmpegLocation() {
    return FFMPEG_LOCATION;
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
  public void addAudioInput(@NotNull AudioInput input) {
    audioInputs.put(input.getId(), input);
  }

  @Override
  public void removeAudioInput(@NotNull AudioInput input) {
    audioInputs.remove(input.getId());
  }

  @Override
  public @NotNull String createStreamUrl(@NotNull ContentStream stream, @Nullable Duration ttl) {
    return context.getBean(MediaController.class).createStreamUrl(stream,
      ttl == null ? Integer.MAX_VALUE : (int) ttl.getSeconds());
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

  public void interactWebDriver(String entityID, InteractWebDriverRequest request) {
    WebDriverContext webDriverContext = webDriverInteracts.get(entityID);
    if (webDriverContext != null) {
      request.handle(webDriverContext);
    }
  }

  @Getter
  @Setter
  public static class InteractWebDriverRequest {
    private Integer x;
    private Integer y;
    private Double width;
    private Double height;
    private boolean leftClick;
    private boolean rightClick;
    private String key;

    public void handle(WebDriverContext webDriverContext) {
      if (x != null && y != null && width != null && height != null) {
        if (webDriverContext.size == null) {
          // Get window height and width using JavaScript
          JavascriptExecutor jsExecutor = (JavascriptExecutor) webDriverContext.driver;
          Long width = (Long) jsExecutor.executeScript("return window.innerWidth;");
          Long height = (Long) jsExecutor.executeScript("return window.innerHeight;");
          if (width == null || height == null) {
            return;
          }
          webDriverContext.size = Pair.of(width.intValue(), height.intValue());
        }
        double driverWidth = webDriverContext.size.getLeft();
        double driverHeight = webDriverContext.size.getRight();
        double relativeX = (x / width) * driverWidth;
        double relativeY = (y / height) * driverHeight;

        JavascriptExecutor js = (JavascriptExecutor) webDriverContext.driver;
        webDriverContext.currentElement = (WebElement) js.executeScript(
          "let el = document.elementFromPoint(arguments[0], arguments[1]);" +
          "while (el && !el.matches('a, button, [role=\"button\"], input')) { el = el.parentElement; }" +
          "return el;",
          relativeX,
          relativeY
        );
        if (webDriverContext.currentElement == null) {
          log.warn("Unable to find any clickable element on WebDriver");
        } else {
          log.info("Found element on WebDriver: {} - {}",
            webDriverContext.currentElement.getTagName(), webDriverContext.currentElement.getText());
        }
      }

      var el = webDriverContext.currentElement;
      if (el != null) {
        if (leftClick) {
          el.click();
        } else if (key != null) {
          sendKeyCommands(el);
        }
      }
    }

    private void sendKeyCommands(WebElement el) {
      for (int i = 0; i < key.length(); i++) {
        char c = key.charAt(i);
        if (c == '{') {
          int commandEnd = key.indexOf('}', i);
          if (commandEnd != -1) {
            String command = key.substring(i, commandEnd + 1);
            if (command.equals("{bksp}")) {
              el.sendKeys(Keys.BACK_SPACE);
              i = commandEnd;
              continue;
            } else if (command.equals("{enter}")) {
              el.sendKeys(Keys.ENTER);
              i = commandEnd;
              continue;
            } else if (command.equals("{tab}")) {
              el.sendKeys(Keys.TAB);
              i = commandEnd;
              continue;
            } else if (command.equals("{space}")) {
              el.sendKeys(Keys.SPACE);
              i = commandEnd;
              continue;
            }
          }
        }
        el.sendKeys(String.valueOf(c));
      }
    }
  }

  @RequiredArgsConstructor
  public static class WebDriverContext {
    private final WebDriver driver;
    public Pair<Integer, Integer> size;
    public WebElement currentElement;
  }
}

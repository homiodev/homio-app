package org.homio.addon.media;

import com.pivovarit.function.ThrowingFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.service.TextToSpeechEntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.StreamFormat;
import org.homio.api.stream.StreamPlayer;
import org.homio.api.stream.audio.AudioFormat;
import org.homio.api.stream.impl.ByteArrayContentStream;
import org.homio.api.stream.impl.FileContentStream;
import org.homio.api.stream.impl.ResourceContentStream;
import org.homio.api.stream.impl.URLContentStream;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.common.ContextImpl;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
@Getter
@Component
public class Scratch3MediaBlocks extends Scratch3ExtensionBlocks {

  private final MenuBlock.ServerMenuBlock mediaMenu;
  private final MenuBlock.ServerMenuBlock ttsMenu;
  // private final MenuBlock.ServerMenuBlock audioSourceMenu;
  private final MenuBlock.ServerMenuBlock audioOutputMenu;
  // private final Scratch3Block playSourceCommand;
  private final MenuBlock.StaticMenuBlock<AudioInfo> infoMenu;
  private final MenuBlock.ServerMenuBlock videoOutputMenu;
  private final MenuBlock.ServerMenuBlock streamMenu;

  public Scratch3MediaBlocks(Context context) {
    super("#87B023", context, null, "media");
    setParent(ScratchParent.media);

    // menu
    this.ttsMenu = menuServerServiceItems("tts", TextToSpeechEntityService.class, "Select TTS");
    this.mediaMenu = menuServerFiles(".(mp3|wav|ogg|aac|webrtc|webm|ogv|flv|avi|mp4|ts|m3u8|mjpeg)");

    this.audioOutputMenu = menuServer("aoMenu", "rest/media/stream?filter=audio", "Audio speaker");
    this.videoOutputMenu = menuServer("voMenu", "rest/media/stream?filter=video", "Video output");
    this.streamMenu = menuServer("streamMenu", "rest/media/stream", "Output");
    this.infoMenu = menuStatic("infoMenu", AudioInfo.class, AudioInfo.Length);

    blockCommand(
      10,
      "play",
      "Play [FILE] to [SPEAKER]|Volume [VOLUME]",
      workspaceBlock -> playFileCommand(workspaceBlock, resource -> playResource(resource, workspaceBlock, null, null)),
      block -> {
        block.addArgument("FILE", mediaMenu);
        block.addArgument("SPEAKER", streamMenu);
        block.addArgument("VOLUME", 100);
      });

    blockCommand(
      12,
      "play_part",
      "Play [FILE] to [SPEAKER]|Volume [VOLUME], From [FROM]sec. Length [LENGTH]sec.",
      this::playPartFileCommand,
      block -> {
        block.addArgument("FILE", mediaMenu);
        block.addArgument("SPEAKER", streamMenu);
        block.addArgument("VOLUME", 100);
        block.addArgument("FROM", 10);
        block.addArgument("LENGTH", 10);
      });

        /* this.playSourceCommand = blockCommand(15, "play_src",
                 "Play source [SOURCE] to [SPEAKER]|Volume [VOLUME]", this::playSourceCommand);
        this.playSourceCommand.addArgument("SOURCE", this.audioSourceMenu);
        this.playSourceCommand.addArgument("SPEAKER", this.speakerMenu);
        this.playSourceCommand.addArgument("VOLUME", 100); */

    blockCommand(
      20,
      "stop",
      "Stop [SPEAKER]",
      this::stopCommand,
      block -> block.addArgument("SPEAKER", streamMenu));

    blockCommand(
      30,
      "resume",
      "Resume [SPEAKER]",
      this::resumeCommand,
      block -> block.addArgument("SPEAKER", streamMenu));

    blockReporter(
      100,
      "info",
      "[FILE] [VALUE]",
      this::getInfoReporter,
      block -> {
        block.addArgument("FILE", this.mediaMenu);
        block.addArgument(VALUE, this.infoMenu);
      });

    blockReporter(
      130,
      "tts",
      "text [VALUE] to audio [TTS]",
      this::getTextToAudioReporter,
      block -> {
        block.addArgument(VALUE, "Hello world");
        block.addArgument("TTS", this.ttsMenu);
      });
  }

  private static float getAudioLength(AudioHeader audioHeader) {
    if (audioHeader instanceof GenericAudioHeader) {
      return ((GenericAudioHeader) audioHeader).getPreciseLength();
    } else if (audioHeader instanceof MP3AudioHeader) {
      return (float) ((MP3AudioHeader) audioHeader).getPreciseTrackLength();
    }
    throw new IllegalArgumentException("Unable to get length from audio length");
  }

  private RawType getTextToAudioReporter(WorkspaceBlock workspaceBlock) {
    String text = workspaceBlock.getInputString(VALUE);
    if (!text.isEmpty()) {
      TextToSpeechEntityService ttsService =
        workspaceBlock.getEntityService(
          "TTS", this.ttsMenu, TextToSpeechEntityService.class);
      return new RawType(ttsService.synthesizeSpeech(text, true), "audio/mp3");
    }
    return null;
  }

  private State getInfoReporter(WorkspaceBlock workspaceBlock) throws Exception {
    return handleFile(
      workspaceBlock,
      resource ->
        workspaceBlock.getMenuValue(VALUE, this.infoMenu).handler.apply(resource.getFile()));
  }

  private <T> T handleFile(
    WorkspaceBlock workspaceBlock, ThrowingFunction<Resource, T, Exception> handler)
    throws Exception {
    Resource resource = workspaceBlock.getFile("FILE", this.mediaMenu, true);
    return handler.apply(resource);
  }

  private void stopCommand(WorkspaceBlock workspaceBlock) {
    StreamPlayer player = getStreamPlayer(workspaceBlock, streamMenu);
    if (player != null) {
      player.stop();
    }
  }

  private void resumeCommand(WorkspaceBlock workspaceBlock) {
    StreamPlayer player = getStreamPlayer(workspaceBlock, streamMenu);
    if (player != null) {
      player.resume();
    }
  }

  private void playPartFileCommand(WorkspaceBlock workspaceBlock) throws Exception {
    handleFile(
      workspaceBlock,
      (ThrowingFunction<Resource, Void, Exception>)
        resource -> {
          File file = resource.getFile();
          int from = workspaceBlock.getInputIntegerRequired("FROM");
          int length = workspaceBlock.getInputIntegerRequired("LENGTH");

          AudioHeader audioHeader = AudioFileIO.read(file).getAudioHeader();
          float trackLength = getAudioLength(audioHeader);
          long frameCount = getAudioFrames(audioHeader, file);
          float framesPerSeconds = frameCount / trackLength;

          from *= (int) framesPerSeconds;
          int to = (int) (from + length * framesPerSeconds);

          playResource(resource, workspaceBlock, Math.abs(from), Math.abs(to));
          return null;
        });
  }

  private long getAudioFrames(AudioHeader audioHeader, File file)
    throws IOException, UnsupportedAudioFileException {
    if (audioHeader instanceof MP3AudioHeader) {
      return ((MP3AudioHeader) audioHeader).getNumberOfFrames();
    } else {
      return AudioSystem.getAudioInputStream(file).getFrameLength();
    }
  }

  private void playFileCommand(WorkspaceBlock workspaceBlock, Consumer<Resource> handler) throws Exception {
    handleFile(
      workspaceBlock,
      (ThrowingFunction<Resource, Void, Exception>)
        resource -> {
          handler.accept(resource);
          return null;
        });
  }

  private void playResource(Resource resource, WorkspaceBlock workspaceBlock, Integer from, Integer to) {
    ContentStream stream = getContentStream(resource);
    Integer volume = workspaceBlock.getInputInteger("VOLUME", null);
    StreamPlayer player = getStreamPlayer(workspaceBlock, streamMenu);
    if (player != null) {
      Integer oldVolume = volume == null ? null : getVolume(player);
      if (!Objects.equals(oldVolume, volume)) {
        setVolume(player, volume);
      }
      try {
        player.play(stream, from, to);
      } catch (Exception e) {
        log.warn("Error playing '{}': {}", stream, e.getMessage(), e);
      } finally {
        if (!Objects.equals(oldVolume, volume)) {
          setVolume(player, oldVolume);
        }
      }
    }
  }

  private @Nullable StreamPlayer getStreamPlayer(WorkspaceBlock workspaceBlock, MenuBlock.ServerMenuBlock streamMenu) {
    String speakerId = workspaceBlock.getMenuValue("SPEAKER", streamMenu);
    return ((ContextImpl) context).media().getPlayer(speakerId);
  }

  private Integer getVolume(StreamPlayer player) {
    try {
      // get current volume
      return player.getVolume();
    } catch (IOException e) {
      log.debug(
        "An exception occurred while getting the volume of audioSpeaker '{}' : {}",
        player.getId(),
        e.getMessage(),
        e);
    }
    return null;
  }

  private void setVolume(StreamPlayer player, Integer value) {
    if (value != null) {
      try {
        // get current volume
        player.setVolume(value);
      } catch (IOException e) {
        log.debug(
          "An exception occurred while setting the volume of audioSpeaker '{}' : {}",
          player.getId(),
          e.getMessage(),
          e);
      }
    }
  }

  @SneakyThrows
  private ContentStream getContentStream(Resource resource) {
    StreamFormat streamFormat = AudioFormat.MP3;
    String filename = resource.getFilename();
    if (filename != null) {
      streamFormat = StreamFormat.evaluateFormat(filename);
    }
    if (resource.isFile()) {
      return new FileContentStream(resource.getFile(), streamFormat);
    }
    if (resource instanceof UrlResource url) {
      return new URLContentStream(url.getURL(), streamFormat);
    }
    if (resource instanceof ByteArrayResource bytes) {
      return new ByteArrayContentStream(bytes.getByteArray(), streamFormat);
    }
    return new ResourceContentStream(resource, streamFormat);
  }

  @RequiredArgsConstructor
  private enum AudioInfo {
    Length(file -> new DecimalType(getAudioLength(AudioFileIO.read(file).getAudioHeader()))),
    BitRate(
      file ->
        new DecimalType(
          AudioFileIO.read(file).getAudioHeader().getBitRateAsNumber())),
    SampleRate(
      file ->
        new DecimalType(
          AudioFileIO.read(file).getAudioHeader().getSampleRateAsNumber())),
    Format(file -> new DecimalType(AudioFileIO.read(file).getAudioHeader().getFormat())),
    Channels(file -> new DecimalType(AudioFileIO.read(file).getAudioHeader().getChannels()));

    public final ThrowingFunction<File, State, Exception> handler;
  }
}

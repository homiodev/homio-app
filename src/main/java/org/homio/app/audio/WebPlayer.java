package org.homio.app.audio;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.api.stream.impl.URLContentStream;
import org.homio.api.stream.video.VideoPlayer;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.time.Duration;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
@RequiredArgsConstructor
public class WebPlayer implements AudioPlayer, VideoPlayer {

  public static final String ID = "WebPlayer";

  private final ContextImpl context;

  @Setter
  @Getter
  private int volume = 100;

  private static AudioFormat.@NotNull Encoding getEncoding(org.homio.api.stream.audio.AudioFormat audioFormat) {
    String codec = audioFormat.getCodec();
    if (org.homio.api.stream.audio.AudioFormat.CODEC_PCM_SIGNED.equals(codec)) {
      return AudioFormat.Encoding.PCM_SIGNED;
    } else if (org.homio.api.stream.audio.AudioFormat.CODEC_PCM_ULAW.equals(codec)) {
      return AudioFormat.Encoding.ULAW;
    } else if (org.homio.api.stream.audio.AudioFormat.CODEC_PCM_ALAW.equals(codec)) {
      return AudioFormat.Encoding.ALAW;
    }
    return new AudioFormat.Encoding(codec);
  }

  @Override
  public void play(@NotNull ContentStream stream, @Nullable Integer startFrame, @Nullable Integer endFrame) {
    log.debug("Received audio stream of format {}", stream.getStreamFormat());
    if (stream instanceof URLContentStream urlContentStream) {
      // it is an external URL, so we can directly pass this on.
      sendStreamToWeb(urlContentStream.getURL().toString(), stream.getStreamFormat().getMimeType());
    } else {
      String url = "$DEVICE_URL/" + context.media().createStreamUrl(stream, Duration.ofSeconds(60));
      sendStreamToWeb(url, stream.getStreamFormat().getMimeType());
    }
  }

  private void sendStreamToWeb(@NotNull String url, @NotNull MimeType mimeType) {
    ObjectNode params = OBJECT_MAPPER.createObjectNode()
      .put("volume", volume)
      .put("mimeType", mimeType.getType())
      .put("mimeSubType", mimeType.getSubtype());
    context.ui().sendGlobal(ContextUIImpl.GlobalSendType.stream, String.valueOf(url.hashCode()), url, null, params);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void stop() {
    context.ui().sendGlobal(ContextUIImpl.GlobalSendType.audio, "", "stop", null, null);
  }

  @Override
  public void pause() {
    context.ui().sendGlobal(ContextUIImpl.GlobalSendType.audio, "", "pause", null, null);
  }

  protected @Nullable AudioFormat convertAudioFormat(org.homio.api.stream.audio.AudioFormat audioFormat) {
    AudioFormat.Encoding encoding = getEncoding(audioFormat);
    final Long frequency = audioFormat.getFrequency();
    if (frequency == null) {
      return null;
    }
    final float sampleRate = frequency.floatValue();

    final Integer bitDepth = audioFormat.getBitDepth();
    if (bitDepth == null) {
      return null;
    }
    final int sampleSizeInBits = bitDepth;

    final int channels = 1;

    final int frameSize = sampleSizeInBits / 8;

    final float frameRate = sampleRate / frameSize;

    final Boolean bigEndian = audioFormat.getBigEndian();
    if (bigEndian == null) {
      return null;
    }

    return new AudioFormat(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian);
  }

  public Thread newThread(ContentStream stream) {
    return new Thread(() -> {
      SourceDataLine line;
      AudioFormat audioFormat = convertAudioFormat((org.homio.api.stream.audio.AudioFormat) stream.getStreamFormat());
      if (audioFormat == null) {
        log.warn("Audio format is unsupported or does not have enough details in order to be played");
        return;
      }
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
      try {
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(audioFormat);
      } catch (Exception e) {
        log.warn("No line found: {}", e.getMessage());
        log.info("Available lines are:");
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo(); // get available mixers
        Mixer mixer;
        for (Mixer.Info value : mixerInfo) {
          mixer = AudioSystem.getMixer(value);
          Line.Info[] lineInfos = mixer.getSourceLineInfo();
          for (Line.Info lineInfo : lineInfos) {
            log.info("{}", lineInfo);
          }
        }
        return;
      }
      Resource resource = stream.getResource();
      try (InputStream inputStream = resource.getInputStream()) {
        line.start();
        int nRead;
        byte[] abData = new byte[65532]; // needs to be a multiple of 4 and 6, to support both 16 and 24 bit stereo
        while ((nRead = inputStream.read(abData, 0, abData.length)) != -1) {
          line.write(abData, 0, nRead);
        }
      } catch (Exception e) {
        log.error("Error while getting audio input stream: {}", e.getMessage());
      } finally {
        line.drain();
        line.close();
        IOUtils.closeQuietly(stream);
      }
    });
  }
}

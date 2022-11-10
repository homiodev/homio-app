package org.touchhome.app.audio.javasound;

import java.util.Collections;
import java.util.Set;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.audio.AudioFormat;
import org.touchhome.bundle.api.audio.AudioSource;
import org.touchhome.bundle.api.audio.AudioStream;
import org.touchhome.bundle.api.audio.stream.JavaSoundInputStream;

@Component
public class JavaSoundAudioSource implements AudioSource {

  /**
   * Java Sound audio format
   */
  private final javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(16000.0f, 16, 1, true,
      false);

  /**
   * AudioFormat of the JavaSoundAudioSource
   */
  private final AudioFormat audioFormat = convertAudioFormat(format);

  /**
   * TargetDataLine for the mic
   */
  private @Nullable TargetDataLine microphone;

  /**
   * Constructs a JavaSoundAudioSource
   */
  public JavaSoundAudioSource() {
  }

  private static AudioFormat convertAudioFormat(javax.sound.sampled.AudioFormat audioFormat) {
    String container = AudioFormat.CONTAINER_WAVE;

    String codec = audioFormat.getEncoding().toString();

    Boolean bigEndian = audioFormat.isBigEndian();

    int frameSize = audioFormat.getFrameSize(); // In bytes
    int bitsPerFrame = frameSize * 8;
    Integer bitDepth = ((AudioSystem.NOT_SPECIFIED == frameSize) ? null : bitsPerFrame);

    float frameRate = audioFormat.getFrameRate();
    Integer bitRate = ((AudioSystem.NOT_SPECIFIED == frameRate) ? null
        : (int) (frameRate * bitsPerFrame));

    float sampleRate = audioFormat.getSampleRate();
    Long frequency = ((AudioSystem.NOT_SPECIFIED == sampleRate) ? null : (long) sampleRate);

    return new AudioFormat(container, codec, bigEndian, bitDepth, bitRate, frequency);
  }

  private TargetDataLine initMicrophone(javax.sound.sampled.AudioFormat format) {
    try {
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
      TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);

      microphone.open(format);

      this.microphone = microphone;
      return microphone;
    } catch (Exception e) {
      throw new RuntimeException("Error creating the audio input stream.", e);
    }
  }

  @Override
  public synchronized AudioStream getInputStream(AudioFormat expectedFormat) {
    if (!expectedFormat.isCompatible(audioFormat)) {
      throw new IllegalStateException("Cannot produce streams in format " + expectedFormat);
    }
    TargetDataLine mic = this.microphone;
    if (mic == null) {
      mic = initMicrophone(format);
    }
    return new JavaSoundInputStream(mic, audioFormat);
  }

  @Override
  public String toString() {
    return "javasound";
  }

  @Override
  public String getEntityID() {
    return "javasound";
  }

  @Override
  public Set<AudioFormat> getSupportedFormats() {
    return Collections.singleton(audioFormat);
  }
}

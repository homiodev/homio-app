package org.homio.app.audio;

import org.homio.api.stream.audio.AudioFormat;
import org.homio.api.stream.audio.AudioStream;
import org.homio.api.stream.audio.JavaSoundInputStream;
import org.homio.api.stream.audio.MicrophoneInput;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Collections;
import java.util.Set;

public class BuildInMicrophoneInput implements MicrophoneInput {

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
    public String getId() {
        return "BuildInMicrophone";
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return Collections.singleton(audioFormat);
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
}

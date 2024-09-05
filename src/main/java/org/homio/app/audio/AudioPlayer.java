package org.homio.app.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.stream.audio.AudioStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.*;
import java.io.InputStream;

@Log4j2
@RequiredArgsConstructor
public class AudioPlayer extends Thread {

    /**
     * The AudioStream to play
     */
    private final AudioStream audioStream;

    /**
     * This method plays the contained AudioSource
     */
    @Override
    public void run() {
        SourceDataLine line;
        AudioFormat audioFormat = convertAudioFormat(this.audioStream.getFormat());
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
        try (InputStream inputStream = audioStream.getInputStream()) {
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
            IOUtils.closeQuietly(audioStream);
        }
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

        final Boolean bigEndian = audioFormat.isBigEndian();
        if (bigEndian == null) {
            return null;
        }

        return new AudioFormat(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian);
    }

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
}

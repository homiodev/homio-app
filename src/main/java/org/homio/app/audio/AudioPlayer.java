package org.homio.app.audio;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.bundle.api.audio.AudioStream;
import org.jetbrains.annotations.Nullable;

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
        line.start();
        int nRead = 0;
        byte[] abData = new byte[65532]; // needs to be a multiple of 4 and 6, to support both 16 and 24 bit stereo
        try {
            while (-1 != nRead) {
                nRead = audioStream.read(abData, 0, abData.length);
                if (nRead >= 0) {
                    line.write(abData, 0, nRead);
                }
            }
        } catch (IOException e) {
            log.error("Error while playing audio: {}", e.getMessage());
        } finally {
            line.drain();
            line.close();
            try {
                audioStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    protected @Nullable AudioFormat convertAudioFormat(org.homio.bundle.api.audio.AudioFormat audioFormat) {
        String codec = audioFormat.getCodec();
        AudioFormat.Encoding encoding = new AudioFormat.Encoding(codec);
        if (org.homio.bundle.api.audio.AudioFormat.CODEC_PCM_SIGNED.equals(codec)) {
            encoding = AudioFormat.Encoding.PCM_SIGNED;
        } else if (org.homio.bundle.api.audio.AudioFormat.CODEC_PCM_ULAW.equals(codec)) {
            encoding = AudioFormat.Encoding.ULAW;
        } else if (org.homio.bundle.api.audio.AudioFormat.CODEC_PCM_ALAW.equals(codec)) {
            encoding = AudioFormat.Encoding.ALAW;
        }

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
}

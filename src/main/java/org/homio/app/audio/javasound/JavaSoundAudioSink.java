package org.homio.app.audio.javasound;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Port;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.audio.AudioFormat;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.AudioStream;
import org.homio.app.audio.AudioPlayer;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class JavaSoundAudioSink implements AudioSink {

    private static final Set<AudioFormat> SUPPORTED_AUDIO_FORMATS =
        new HashSet<>(Arrays.asList(AudioFormat.MP3, AudioFormat.WAV));
    private static final Set<Class<? extends AudioStream>> SUPPORTED_AUDIO_STREAMS =
        new HashSet<>(Collections.singletonList(AudioStream.class));
    private static AdvancedPlayer streamPlayer;
    private final EntityContext entityContext;
    // for resume
    private Integer pausedOnFrame;
    private AudioStream audioStream;
    private String sinkSource;

    @Override
    public Map<String, String> getSources() {
        Map<String, String> sources = new HashMap<>();
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : infos) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(Port.Info.SPEAKER)) {
                sources.put("system:" + mixer.getMixerInfo().getName(), "System:" + mixer.getMixerInfo().getName());
            }
        }
        return sources;
    }

    @Override
    public void resume() {
        if (pausedOnFrame != null) {
            play(audioStream, sinkSource, pausedOnFrame, null);
        }
    }

    @Override
    public void play(AudioStream audioStream, String sinkSource, Integer startFrame, Integer endFrame) {
        this.audioStream = audioStream;
        this.sinkSource = sinkSource;

        if (!Objects.equals(audioStream.getFormat().getCodec(), AudioFormat.CODEC_MP3)) {
            AudioPlayer audioPlayer = new AudioPlayer(audioStream);
            audioPlayer.start();
            try {
                audioPlayer.join();
            } catch (InterruptedException e) {
                log.error("Playing audio has been interrupted.");
            }
        } else {
            pause();
            pausedOnFrame = null;
            try {
                // we start a new continuous stream and store its handle
                streamPlayer = new AdvancedPlayer(audioStream);
                playInThread(streamPlayer, startFrame, endFrame);
            } catch (JavaLayerException e) {
                log.error("An exception occurred while playing url audio stream : '{}'", e.getMessage());
            }
        }
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return SUPPORTED_AUDIO_FORMATS;
    }

    @Override
    public Set<Class<? extends AudioStream>> getSupportedStreams() {
        return SUPPORTED_AUDIO_STREAMS;
    }

    @Override
    public String getId() {
        return "javasound";
    }

    @Override
    public @Nullable String getLabel(@Nullable Locale locale) {
        return "System Speaker";
    }

    @Override
    public int pause() {
        if (streamPlayer != null) {
            // if we are already playing a stream, stop it first
            streamPlayer.close();
            streamPlayer = null;
        }
        return 0;
    }

    public int resume(AudioStream audioStream, String sinkSource, Integer startFrame, Integer endFrame) {
        if (streamPlayer != null) {
            play(audioStream, sinkSource, startFrame, endFrame);
            // if we are already playing a stream, stop it first
            streamPlayer.close();
            streamPlayer = null;
        }
        return 0;
    }

    @Override
    public int getVolume() {
        return 100;
    }

    @Override
    public void setVolume(int volume) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume value must be in the range [0,100]!");
        }
        runVolumeCommand((FloatControl input) -> {
            input.setValue(volume / 100f);
            return true;
        });
    }

    private void playInThread(AdvancedPlayer player, Integer from, Integer to) {
        if (player != null) {
            player.setPlayBackListener(new PlaybackListener() {
                @Override
                public void playbackFinished(PlaybackEvent event) {
                    pausedOnFrame = event.getFrame();
                }
            });
            entityContext.bgp().builder("java_sink-audio").execute(() -> {
                try {
                    Integer start = from;
                    Integer end = to;

                    if (start != null || end != null) {
                        if (end != null) {
                            if (start == null) {
                                start = 0;
                            }
                        } else {
                            end = Integer.MAX_VALUE;
                        }

                        player.play(start, end);
                    } else {
                        player.play();
                    }
                } finally {
                    player.close();
                }
            });
        }
    }

    private void runVolumeCommand(Function<FloatControl, Boolean> closure) {
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : infos) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(Port.Info.SPEAKER)) {
                Port port;
                try {
                    port = (Port) mixer.getLine(Port.Info.SPEAKER);
                    port.open();
                    if (port.isControlSupported(FloatControl.Type.VOLUME)) {
                        FloatControl volume = (FloatControl) port.getControl(FloatControl.Type.VOLUME);
                        closure.apply(volume);
                    }
                    port.close();
                } catch (LineUnavailableException e) {
                    log.error("Cannot access master volume control", e);
                }
            }
        }
    }
}

package org.homio.app.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.audio.AudioFormat;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Log4j2
@RequiredArgsConstructor
public class JavaSoundAudioPlayer implements AudioPlayer {

    private static AdvancedPlayer streamPlayer;

    private final ContextImpl context;
    private final Mixer mixer;

    // for resume
    private Integer pausedOnFrame;
    private ContentStream stream;

    @Override
    public void resume() {
        if (pausedOnFrame != null) {
            play(stream, pausedOnFrame, null);
        }
    }

    @Override
    public Set<AudioFormat> getAudioSupportedFormats() {
        return Set.of(AudioFormat.MP3, AudioFormat.WAV, AudioFormat.PCM_SIGNED);
    }

    @SneakyThrows
    @Override
    public void play(ContentStream stream, Integer startFrame, Integer endFrame) {
        this.stream = stream;
        AudioFormat audioFormat = (AudioFormat) stream.getStreamFormat();

        if (!Objects.equals(audioFormat.getCodec(), AudioFormat.CODEC_MP3)) {
            WebPlayer audioPlayer = new WebPlayer(context);
            Thread audioThread = audioPlayer.newThread(stream);
            audioThread.start();
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                log.error("Playing audio has been interrupted.");
            }
        } else {
            pause();
            pausedOnFrame = null;
            try {
                InputStream inputStream = stream.getResource().getInputStream();
                // we start a new continuous stream and store its handle
                streamPlayer = new AdvancedPlayer(inputStream);
                playInThread(streamPlayer, startFrame, endFrame, stream, inputStream);
            } catch (JavaLayerException e) {
                log.error("An exception occurred while playing url audio stream : '{}'", e.getMessage());
            }
        }
    }

    @Override
    public String getId() {
        return "javasound-" + mixer.getMixerInfo().getName();
    }

    @Override
    public @NotNull String getLabel() {
        return mixer.getMixerInfo().getName();
    }

    @Override
    public void stop() {
        if (streamPlayer != null) {
            // if we are already playing a stream, stop it first
            streamPlayer.close();
            streamPlayer = null;
        }
    }

    public int resume(ContentStream stream, Integer startFrame, Integer endFrame) {
        if (streamPlayer != null) {
            play(stream, startFrame, endFrame);
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

    private void playInThread(AdvancedPlayer player, Integer from, Integer to, ContentStream stream, InputStream inputStream) {
        if (player != null) {
            player.setPlayBackListener(new PlaybackListener() {
                @Override
                public void playbackFinished(PlaybackEvent event) {
                    pausedOnFrame = event.getFrame();
                }
            });
            context.bgp().builder("java_speaker-audio").execute(() -> {
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
                    inputStream.close();
                    stream.close();
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

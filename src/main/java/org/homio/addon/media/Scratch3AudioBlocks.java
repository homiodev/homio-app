package org.homio.addon.media;

import com.pivovarit.function.ThrowingFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.stream.FileAudioStream;
import org.homio.api.service.TextToSpeechEntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.audio.AudioService;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

@Log4j2
@Getter
@Component
public class Scratch3AudioBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.ServerMenuBlock audioMenu;
    private final MenuBlock.ServerMenuBlock ttsMenu;
    // private final MenuBlock.ServerMenuBlock audioSourceMenu;
    private final MenuBlock.ServerMenuBlock sinkMenu;
    private final AudioService audioService;
    // private final Scratch3Block playSourceCommand;
    private final MenuBlock.StaticMenuBlock<AudioInfo> infoMenu;

    public Scratch3AudioBlocks(Context context, AudioService audioService) {
        super("#87B023", context, null, "audio");
        this.audioService = audioService;
        setParent(ScratchParent.media);

        // menu
        this.ttsMenu = menuServerServiceItems("tts", TextToSpeechEntityService.class, "Select TTS");
        this.audioMenu = menuServer("audioMenu", "rest/media/audio", "Audio").setUIDelimiter("/");

        this.sinkMenu = menuServer("sinkMenu", "rest/media/sink", "Sink");
        this.infoMenu = menuStatic("infoMenu", AudioInfo.class, AudioInfo.Length);
        // this.audioSourceMenu = menuServer("asMenu", "rest/media/audioSource");

        blockCommand(
                10,
                "play",
                "Play file [FILE] to [SINK]|Volume [VOLUME]",
                this::playFileCommand,
                block -> {
                    block.addArgument("FILE", this.audioMenu);
                    block.addArgument("SINK", this.sinkMenu);
                    block.addArgument("VOLUME", 100);
                });

        blockCommand(
                12,
                "play_part",
                "Play file [FILE] to [SINK]|Volume [VOLUME], From [FROM]sec. Length [LENGTH]sec.",
                this::playPartFileCommand,
                block -> {
                    block.addArgument("FILE", this.audioMenu);
                    block.addArgument("SINK", this.sinkMenu);
                    block.addArgument("VOLUME", 100);
                    block.addArgument("FROM", 10);
                    block.addArgument("LENGTH", 10);
                });

        /* this.playSourceCommand = blockCommand(15, "play_src",
                 "Play source [SOURCE] to [SINK]|Volume [VOLUME]", this::playSourceCommand);
        this.playSourceCommand.addArgument("SOURCE", this.audioSourceMenu);
        this.playSourceCommand.addArgument("SINK", this.sinkMenu);
        this.playSourceCommand.addArgument("VOLUME", 100); */

        blockCommand(
                20,
                "stop",
                "Stop [SINK]",
                this::stopCommand,
                block -> {
                    block.addArgument("SINK", this.sinkMenu);
                });

        blockCommand(
                30,
                "resume",
                "Resume [SINK]",
                this::resumeCommand,
                block -> {
                    block.addArgument("SINK", this.sinkMenu);
                });

        blockReporter(
                100,
                "info",
                "[FILE] [VALUE]",
                this::getInfoReporter,
                block -> {
                    block.addArgument("FILE", this.audioMenu);
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
                file -> workspaceBlock.getMenuValue(VALUE, this.infoMenu).handler.apply(file));
    }

    private <T> T handleFile(
            WorkspaceBlock workspaceBlock, ThrowingFunction<File, T, Exception> handler)
            throws Exception {
        Path path = workspaceBlock.getFile("FILE", this.audioMenu, true);
        return handler.apply(path.toFile());
    }

    private void stopCommand(WorkspaceBlock workspaceBlock) {
        AudioSinkSource audioSinkSource = getSink(workspaceBlock);
        if (audioSinkSource != null) {
            AudioSink audioSink = audioSinkSource.getSink();
            audioSink.pause();
        }
    }

    private void resumeCommand(WorkspaceBlock workspaceBlock) {
        AudioSinkSource audioSinkSource = getSink(workspaceBlock);
        if (audioSinkSource != null) {
            audioSinkSource.getSink().resume();
        }
    }

    private void playPartFileCommand(WorkspaceBlock workspaceBlock) throws Exception {
        handleFile(
                workspaceBlock,
                (ThrowingFunction<File, Void, Exception>)
                        file -> {
                            int from = workspaceBlock.getInputIntegerRequired("FROM");
                            int length = workspaceBlock.getInputIntegerRequired("LENGTH");

                            AudioHeader audioHeader = AudioFileIO.read(file).getAudioHeader();
                            float trackLength = getAudioLength(audioHeader);
                            long frameCount = getAudioFrames(audioHeader, file);
                            float framesPerSeconds = frameCount / trackLength;

                            from *= framesPerSeconds;
                            int to = (int) (from + length * framesPerSeconds);

                            playAudio(file, workspaceBlock, Math.abs(from), Math.abs(to));
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

    private void playFileCommand(WorkspaceBlock workspaceBlock) throws Exception {
        handleFile(
                workspaceBlock,
                (ThrowingFunction<File, Void, Exception>)
                        file -> {
                            playAudio(file, workspaceBlock, null, null);
                            return null;
                        });
    }

    private void playAudio(File file, WorkspaceBlock workspaceBlock, Integer from, Integer to)
            throws Exception {
        FileAudioStream audioStream = new FileAudioStream(file);
        Integer volume = workspaceBlock.getInputInteger("VOLUME", null);
        AudioSinkSource audioSinkSource = getSink(workspaceBlock);
        if (audioSinkSource != null) {
            AudioSink audioSink = audioSinkSource.getSink();
            Integer oldVolume = volume == null ? null : getVolume(audioSink);
            if (!Objects.equals(oldVolume, volume)) {
                setVolume(audioSink, volume);
            }
            try {
                audioSink.play(audioStream, audioSinkSource.getSource(), from, to);
            } catch (Exception e) {
                log.warn("Error playing '{}': {}", audioStream, e.getMessage(), e);
            } finally {
                if (!Objects.equals(oldVolume, volume)) {
                    setVolume(audioSink, oldVolume);
                }
            }
        }
    }

    private AudioSinkSource getSink(WorkspaceBlock workspaceBlock) {
        String sink = workspaceBlock.getMenuValue("SINK", this.sinkMenu);
        for (AudioSink audioSink : audioService.getAudioSinks().values()) {
            for (Map.Entry<String, String> entry : audioSink.getSources().entrySet()) {
                if (sink.equals(entry.getKey())) {
                    return new AudioSinkSource(audioSink, entry.getKey());
                }
            }
        }
        return null;
    }

    private Integer getVolume(AudioSink sink) {
        try {
            // get current volume
            return sink.getVolume();
        } catch (IOException e) {
            log.debug(
                    "An exception occurred while getting the volume of sink '{}' : {}",
                    sink.getId(),
                    e.getMessage(),
                    e);
        }
        return null;
    }

    private void setVolume(AudioSink sink, Integer value) {
        if (value != null) {
            try {
                // get current volume
                sink.setVolume(value);
            } catch (IOException e) {
                log.debug(
                        "An exception occurred while setting the volume of sink '{}' : {}",
                        sink.getId(),
                        e.getMessage(),
                        e);
            }
        }
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

    @Getter
    @RequiredArgsConstructor
    public static class AudioSinkSource {
        private final AudioSink sink;
        private final String source;
    }
}

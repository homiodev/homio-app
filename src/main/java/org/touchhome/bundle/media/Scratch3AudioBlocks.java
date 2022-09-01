package org.touchhome.bundle.media;

import com.pivovarit.function.ThrowingFunction;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.app.audio.AudioService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.audio.AudioSink;
import org.touchhome.bundle.api.audio.stream.FileAudioStream;
import org.touchhome.bundle.api.service.TextToSpeechEntityService;
import org.touchhome.bundle.api.state.RawType;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

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
    private final Scratch3Block stopCommand;
    private final Scratch3Block playPartFileCommand;

    private final Scratch3Block textToAudioReporter;
    private final Scratch3Block lengthReporter;
    private final Scratch3Block bitrateReporter;
    private final Scratch3Block sampleRateReporter;
    private final Scratch3Block resumeCommand;
    // private final Scratch3Block playSourceCommand;
    private final Scratch3Block playFileCommand;

    public Scratch3AudioBlocks(EntityContext entityContext, AudioService audioService) {
        super("#87B023", entityContext, null, "audio");
        this.audioService = audioService;
        setParent("media");

        this.ttsMenu = MenuBlock.ofServerServiceItems("tts", TextToSpeechEntityService.class, "Select TTS");
        this.audioMenu = MenuBlock.ofServer("audioMenu", "rest/media/audio", "Audio").setUIDelimiter("/");

        this.sinkMenu = MenuBlock.ofServer("sinkMenu", "rest/media/sink", "Sink");
        // this.audioSourceMenu = MenuBlock.ofServer("asMenu", "rest/media/audioSource");

        this.playFileCommand = Scratch3Block.ofHandler(10, "play",
                BlockType.command, "Play file [FILE] to [SINK]|Volume [VOLUME]", this::playFileCommand);
        this.playFileCommand.addArgument("FILE", this.audioMenu);
        this.playFileCommand.addArgument("SINK", this.sinkMenu);
        this.playFileCommand.addArgument("VOLUME", 100);

        this.playPartFileCommand = withFile(Scratch3Block.ofHandler(12, "play_part",
                BlockType.command, "Play file [FILE] to [SINK]|Volume [VOLUME], From [FROM]sec. Length [LENGTH]sec.", this::playPartFileCommand));
        this.playPartFileCommand.addArgument("SINK", this.sinkMenu);
        this.playPartFileCommand.addArgument("VOLUME", 100);
        this.playPartFileCommand.addArgument("FROM", 10);
        this.playPartFileCommand.addArgument("LENGTH", 10);

        /*this.playSourceCommand = Scratch3Block.ofHandler(15, "play_src",
                BlockType.command, "Play source [SOURCE] to [SINK]|Volume [VOLUME]", this::playSourceCommand);
        this.playSourceCommand.addArgument("SOURCE", this.audioSourceMenu);
        this.playSourceCommand.addArgument("SINK", this.sinkMenu);
        this.playSourceCommand.addArgument("VOLUME", 100);*/

        this.stopCommand = Scratch3Block.ofCommand(20, "stop",
                "Stop [SINK]", this::stopCommand);
        this.stopCommand.addArgument("SINK", this.sinkMenu);

        this.resumeCommand = Scratch3Block.ofCommand(30, "resume",
                "Resume [SINK]", this::resumeCommand);
        this.resumeCommand.addArgument("SINK", this.sinkMenu);

        this.lengthReporter = withFile(Scratch3Block.ofReporter(100, "length", "[FILE] Length", this::getLengthReporter));
        this.bitrateReporter = withFile(Scratch3Block.ofReporter(110, "bitrate", "[FILE] BitRate", this::getBitrateReporter));
        this.sampleRateReporter = withFile(Scratch3Block.ofReporter(120, "samplerate", "[FILE] SampleRate", this::getSampleRateReporter));
        this.textToAudioReporter = Scratch3Block.ofReporter(130, "tts", "text [VALUE] to audio [TTS]", this::getTextToAudioReporter);
        this.textToAudioReporter.addArgument(VALUE, "Hello world");
        this.textToAudioReporter.addArgument("TTS", this.ttsMenu);
    }

    private RawType getTextToAudioReporter(WorkspaceBlock workspaceBlock) {
        String text = workspaceBlock.getInputString(VALUE);
        if (!text.isEmpty()) {
            TextToSpeechEntityService ttsService = workspaceBlock.getEntityService("TTS", this.ttsMenu, TextToSpeechEntityService.class);
            return new RawType(ttsService.synthesizeSpeech(text, true), "audio/mp3");
        }
        return null;
    }

    private float getLengthReporter(WorkspaceBlock workspaceBlock) throws Exception {
        return handleFile(workspaceBlock, file -> getAudioLength(AudioFileIO.read(file).getAudioHeader()));
    }

    private long getBitrateReporter(WorkspaceBlock workspaceBlock) throws Exception {
        return handleFile(workspaceBlock, file -> AudioFileIO.read(file).getAudioHeader().getBitRateAsNumber());
    }

    private int getSampleRateReporter(WorkspaceBlock workspaceBlock) throws Exception {
        return handleFile(workspaceBlock, file -> AudioFileIO.read(file).getAudioHeader().getSampleRateAsNumber());
    }

    private <T> T handleFile(WorkspaceBlock workspaceBlock, ThrowingFunction<File, T, Exception> handler) throws Exception {
        Path path = workspaceBlock.getFile("FILE", this.audioMenu, true);
        return handler.apply(path.toFile());
    }

    private void stopCommand(WorkspaceBlock workspaceBlock) {
        Pair<AudioSink, String> pair = getSink(workspaceBlock);
        if (pair != null) {
            AudioSink audioSink = pair.getFirst();
            audioSink.pause();
        }
    }

    private void resumeCommand(WorkspaceBlock workspaceBlock) {
        Pair<AudioSink, String> pair = getSink(workspaceBlock);
        if (pair != null) {
            pair.getFirst().resume();
        }
    }

    private void playPartFileCommand(WorkspaceBlock workspaceBlock) throws Exception {
        handleFile(workspaceBlock, (ThrowingFunction<File, Void, Exception>) file -> {
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

    private float getAudioLength(AudioHeader audioHeader) {
        if (audioHeader instanceof GenericAudioHeader) {
            return ((GenericAudioHeader) audioHeader).getPreciseLength();
        } else if (audioHeader instanceof MP3AudioHeader) {
            return (float) ((MP3AudioHeader) audioHeader).getPreciseTrackLength();
        }
        throw new IllegalArgumentException("Unable to get length from audio length");
    }

    private long getAudioFrames(AudioHeader audioHeader, File file) throws IOException, UnsupportedAudioFileException {
        if (audioHeader instanceof MP3AudioHeader) {
            return ((MP3AudioHeader) audioHeader).getNumberOfFrames();
        } else {
            AudioSystem.getAudioInputStream(file).getFrameLength();
        }
        return 0;
    }

    private void playFileCommand(WorkspaceBlock workspaceBlock) throws Exception {
        handleFile(workspaceBlock, (ThrowingFunction<File, Void, Exception>) file -> {
            playAudio(file, workspaceBlock, null, null);
            return null;
        });
    }

    private void playAudio(File file, WorkspaceBlock workspaceBlock, Integer from, Integer to) throws Exception {
        FileAudioStream audioStream = new FileAudioStream(file);
        Integer volume = workspaceBlock.getInputInteger("VOLUME", null);
        Pair<AudioSink, String> pair = getSink(workspaceBlock);
        if (pair != null) {
            AudioSink audioSink = pair.getFirst();
            Integer oldVolume = volume == null ? null : getVolume(audioSink);
            if (!Objects.equals(oldVolume, volume)) {
                setVolume(audioSink, volume);
            }
            try {
                audioSink.play(audioStream, pair.getSecond(), from, to);
            } catch (Exception e) {
                log.warn("Error playing '{}': {}", audioStream, e.getMessage(), e);
            } finally {
                if (!Objects.equals(oldVolume, volume)) {
                    setVolume(audioSink, oldVolume);
                }
            }
        }
    }

    private Pair<AudioSink, String> getSink(WorkspaceBlock workspaceBlock) {
        String sink = workspaceBlock.getMenuValue("SINK", this.sinkMenu);
        for (AudioSink audioSink : audioService.getAudioSinks().values()) {
            for (Map.Entry<String, String> entry : audioSink.getSources().entrySet()) {
                if (sink.equals(entry.getKey())) {
                    return Pair.of(audioSink, entry.getKey());
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
            log.debug("An exception occurred while getting the volume of sink '{}' : {}", sink.getId(),
                    e.getMessage(), e);
        }
        return null;
    }

    private void setVolume(AudioSink sink, Integer value) {
        if (value != null) {
            try {
                // get current volume
                sink.setVolume(value);
            } catch (IOException e) {
                log.debug("An exception occurred while setting the volume of sink '{}' : {}", sink.getId(),
                        e.getMessage(), e);
            }
        }
    }

    private Scratch3Block withFile(Scratch3Block scratch3Block) {
        scratch3Block.addArgument("FILE", this.audioMenu);
        return scratch3Block;
    }

}

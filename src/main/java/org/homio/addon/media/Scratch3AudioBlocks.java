package org.homio.addon.media;

import com.pivovarit.function.ThrowingFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.stream.audio.*;
import org.homio.api.service.TextToSpeechEntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.common.ContextImpl;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

@Log4j2
@Getter
@Component
public class Scratch3AudioBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.ServerMenuBlock audioMenu;
    private final MenuBlock.ServerMenuBlock ttsMenu;
    // private final MenuBlock.ServerMenuBlock audioSourceMenu;
    private final MenuBlock.ServerMenuBlock speakerMenu;
    // private final Scratch3Block playSourceCommand;
    private final MenuBlock.StaticMenuBlock<AudioInfo> infoMenu;

    public Scratch3AudioBlocks(Context context) {
        super("#87B023", context, null, "audio");
        setParent(ScratchParent.media);

        // menu
        this.ttsMenu = menuServerServiceItems("tts", TextToSpeechEntityService.class, "Select TTS");
        this.audioMenu = menuServerFiles(".(mp3|wav|ogg|aac)");

        this.speakerMenu = menuServer("speakerMenu", "rest/media/audioSpeaker", "Speaker");
        this.infoMenu = menuStatic("infoMenu", AudioInfo.class, AudioInfo.Length);

        blockCommand(
                10,
                "play",
                "Play file [FILE] to [SPEAKER]|Volume [VOLUME]",
                this::playFileCommand,
                block -> {
                    block.addArgument("FILE", this.audioMenu);
                    block.addArgument("SPEAKER", this.speakerMenu);
                    block.addArgument("VOLUME", 100);
                });

        blockCommand(
                12,
                "play_part",
                "Play file [FILE] to [SPEAKER]|Volume [VOLUME], From [FROM]sec. Length [LENGTH]sec.",
                this::playPartFileCommand,
                block -> {
                    block.addArgument("FILE", this.audioMenu);
                    block.addArgument("SPEAKER", this.speakerMenu);
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
                block -> block.addArgument("SPEAKER", this.speakerMenu));

        blockCommand(
                30,
                "resume",
                "Resume [SPEAKER]",
                this::resumeCommand,
                block -> block.addArgument("SPEAKER", this.speakerMenu));

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
                resource ->
                        workspaceBlock.getMenuValue(VALUE, this.infoMenu).handler.apply(resource.getFile()));
    }

    private <T> T handleFile(
            WorkspaceBlock workspaceBlock, ThrowingFunction<Resource, T, Exception> handler)
            throws Exception {
        Resource resource = workspaceBlock.getFile("FILE", this.audioMenu, true);
        return handler.apply(resource);
    }

    private void stopCommand(WorkspaceBlock workspaceBlock) {
        AudioSpeaker audioSpeaker = getAudioSpeaker(workspaceBlock);
        if (audioSpeaker != null) {
            audioSpeaker.pause();
        }
    }

    private void resumeCommand(WorkspaceBlock workspaceBlock) {
        AudioSpeaker speaker = getAudioSpeaker(workspaceBlock);
        if (speaker != null) {
            speaker.resume();
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

                            playAudio(resource, workspaceBlock, Math.abs(from), Math.abs(to));
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
                (ThrowingFunction<Resource, Void, Exception>)
                        resource -> {
                            playAudio(resource, workspaceBlock, null, null);
                            return null;
                        });
    }

    private void playAudio(Resource resource, WorkspaceBlock workspaceBlock, Integer from, Integer to) {
        AudioStream audioStream = getAudioStream(resource);
        Integer volume = workspaceBlock.getInputInteger("VOLUME", null);
        AudioSpeaker speaker = getAudioSpeaker(workspaceBlock);
        if (speaker != null) {
            Integer oldVolume = volume == null ? null : getVolume(speaker);
            if (!Objects.equals(oldVolume, volume)) {
                setVolume(speaker, volume);
            }
            try {
                speaker.play(audioStream, from, to);
            } catch (Exception e) {
                log.warn("Error playing '{}': {}", audioStream, e.getMessage(), e);
            } finally {
                if (!Objects.equals(oldVolume, volume)) {
                    setVolume(speaker, oldVolume);
                }
            }
        }
    }

    private AudioSpeaker getAudioSpeaker(WorkspaceBlock workspaceBlock) {
        String speakerId = workspaceBlock.getMenuValue("SPEAKER", this.speakerMenu);
        Collection<AudioSpeaker> speakers = ((ContextImpl) context).media().getAudioSpeakers().values();
        for (AudioSpeaker speaker : speakers) {
            if (speakerId.equals(speaker.getId())) {
                return speaker;
            }
        }
        return null;
    }

    private Integer getVolume(AudioSpeaker speaker) {
        try {
            // get current volume
            return speaker.getVolume();
        } catch (IOException e) {
            log.debug(
                    "An exception occurred while getting the volume of audioSpeaker '{}' : {}",
                    speaker.getId(),
                    e.getMessage(),
                    e);
        }
        return null;
    }

    private void setVolume(AudioSpeaker speaker, Integer value) {
        if (value != null) {
            try {
                // get current volume
                speaker.setVolume(value);
            } catch (IOException e) {
                log.debug(
                        "An exception occurred while setting the volume of audioSpeaker '{}' : {}",
                        speaker.getId(),
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

    @SneakyThrows
    private AudioStream getAudioStream(Resource resource) {
        if (resource.isFile()) {
            return new FileAudioStream(resource.getFile());
        }
        if (resource instanceof UrlResource url) {
            return new URLAudioStream(url.getURL(), url.getFilename());
        }
        if (resource instanceof ByteArrayResource bytes) {
            String filename = bytes.getFilename();
            AudioFormat audioFormat = AudioFormat.MP3;
            if (filename != null) {
                audioFormat = AudioStream.evaluateFormat(filename);
            }
            return new ByteArrayAudioStream(bytes.getByteArray(), audioFormat);
        }
        return AudioStream.fromUnknownStream(resource.getInputStream(), AudioFormat.MP3);
    }
}

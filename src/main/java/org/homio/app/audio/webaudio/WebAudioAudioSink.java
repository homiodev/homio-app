package org.homio.app.audio.webaudio;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.audio.AudioFormat;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.AudioStream;
import org.homio.api.audio.stream.FixedLengthAudioStream;
import org.homio.api.audio.stream.URLAudioStream;
import org.homio.app.audio.AudioService;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class WebAudioAudioSink implements AudioSink {

    private static final Set<AudioFormat> SUPPORTED_AUDIO_FORMATS =
            new HashSet<>(Arrays.asList(AudioFormat.MP3, AudioFormat.WAV));
    private static final Set<Class<? extends AudioStream>> SUPPORTED_AUDIO_STREAMS =
            new HashSet<>(Arrays.asList(FixedLengthAudioStream.class, URLAudioStream.class));

    private final AudioService audioService;
    private final Context context;

    @Override
    public void play(AudioStream audioStream, String sinkSource, Integer startFrame, Integer endFrame) {
        if (audioStream == null) {
            // in case the audioStream is null, this should be interpreted as a request to end any currently playing
            // stream.
            log.debug("Web Audio sink does not support stopping the currently playing stream.");
            return;
        }
        try (AudioStream stream = audioStream) {
            log.debug("Received audio stream of format {}", audioStream.getFormat());
            if (stream instanceof URLAudioStream urlAudioStream) {
                // it is an external URL, so we can directly pass this on.
                ((ContextUIImpl) context.ui()).sendAudio(urlAudioStream.getUrl());
            } else if (stream instanceof FixedLengthAudioStream fixedLengthAudioStream) {
                // we need to serve it for a while and make it available to multiple clients, hence only
                // FixedLengthAudioStreams are supported.
                String url = audioService.createAudioUrl(fixedLengthAudioStream, 60);
                ((ContextUIImpl) context.ui()).sendAudio(url);
            } else {
                throw new IllegalArgumentException(
                        "Web audio sink can only handle FixedLengthAudioStreams and URLAudioStreams: " +
                                audioStream.getClass().getSimpleName());
            }
        } catch (IOException e) {
            log.debug("Error while closing the audio stream: {}", e.getMessage(), e);
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
        return "webaudio";
    }

    @Override
    public Map<String, String> getSources() {
        return Collections.singletonMap("WebAudio", "WebAudio");
    }

    @Override
    public String getLabel(Locale locale) {
        return "Web Audio";
    }

    @Override
    public int getVolume() {
        return 100;
    }

    @Override
    public void setVolume(int volume) throws IOException {
        throw new IOException("Web Audio sink does not support volume level changes.");
    }
}

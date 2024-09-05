package org.homio.app.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.stream.audio.AudioSpeaker;
import org.homio.api.stream.audio.AudioStream;
import org.homio.api.stream.audio.URLAudioStream;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Log4j2
@RequiredArgsConstructor
public class WebAudioSpeaker implements AudioSpeaker {

    public static final String ID = "WebAudio";

    private final Context context;

    @Override
    public void play(@NotNull AudioStream audioStream, @Nullable Integer startFrame, @Nullable Integer endFrame) {
        log.debug("Received audio stream of format {}", audioStream.getFormat());
        if (audioStream instanceof URLAudioStream urlAudioStream) {
            // it is an external URL, so we can directly pass this on.
            ((ContextUIImpl) context.ui()).sendAudio(urlAudioStream.getURL().toString());
        } else {
            String url = context.media().createStreamUrl(audioStream, 60);
            ((ContextUIImpl) context.ui()).sendAudio(url);
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVolume() {
        return 100;
    }

    @Override
    public void setVolume(int volume) throws IOException {
        throw new IOException("Web Audio audioSpeaker does not support volume level changes.");
    }
}

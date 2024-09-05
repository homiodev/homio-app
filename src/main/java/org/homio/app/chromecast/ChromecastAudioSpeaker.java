package org.homio.app.chromecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.stream.audio.AudioSpeaker;
import org.homio.api.stream.audio.AudioStream;
import org.homio.api.state.DecimalType;
import org.homio.api.stream.audio.URLAudioStream;
import org.jetbrains.annotations.NotNull;

import static org.homio.api.stream.audio.AudioFormat.MP3;

@Log4j2
@RequiredArgsConstructor
public class ChromecastAudioSpeaker implements AudioSpeaker {
    private static final String MIME_TYPE_AUDIO_WAV = "audio/wav";
    private static final String MIME_TYPE_AUDIO_MPEG = "audio/mpeg";

    private final @NotNull ChromecastService service;

    @Override
    public String getId() {
        return "chromecast-" + service.getEntityID();
    }

    @Override
    public @NotNull String getLabel() {
        return "Chromecast Audio: " + service.getEntity().getName();
    }

    @Override
    public void stop() {
        service.closeApp(null);
    }

    @Override
    public void pause() {
        AudioSpeaker.super.pause();
    }

    @Override
    public void resume() {
        service.handlePause();
    }

    @Override
    public void play(@NotNull AudioStream audioStream, Integer startFrame, Integer endFrame) throws Exception {
        final String url;
        if (audioStream instanceof URLAudioStream urlAudioStream) {
            url = urlAudioStream.getURL().toString();
            IOUtils.closeQuietly(audioStream);
        } else {
            String relativeUrl = service.context().media().createStreamUrl(audioStream, 60);
            url = service.context().hardware().getServerUrl() + relativeUrl;
        }
        service.playMedia("Notification", url,
                MP3.isCompatible(audioStream.getFormat()) ? MIME_TYPE_AUDIO_MPEG : MIME_TYPE_AUDIO_WAV);
    }

    @Override
    public int getVolume() {
        return service.getEndpoints().get("volume").getValue().intValue();
    }

    @Override
    public void setVolume(int volume) {
        service.getEndpoints().get("volume").setValue(new DecimalType(volume), true);
    }
}

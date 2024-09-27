package org.homio.app.chromecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.state.DecimalType;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.audio.AudioFormat;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.api.stream.impl.URLContentStream;
import org.homio.api.stream.video.VideoPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static org.homio.api.stream.audio.AudioFormat.MP3;

@Log4j2
@RequiredArgsConstructor
public class ChromecastPlayer implements AudioPlayer, VideoPlayer {
    private static final String MIME_TYPE_AUDIO_WAV = "audio/wav";
    private static final String MIME_TYPE_AUDIO_MPEG = "audio/mpeg";

    private final @NotNull ChromecastService service;

    @Override
    public String getId() {
        return "chromecast-" + service.getEntityID();
    }

    @Override
    public @NotNull String getLabel() {
        String name = Objects.toString(service.getEntity().getName(), "Chromecast");
        if (!service.getEntity().getChromecastType().name().startsWith(name)) {
            name = service.getEntity().getChromecastType() + ": " + name;
        }
        return name;
    }

    @Override
    public void stop() {
        service.closeApp(null);
    }

    @Override
    public void pause() {
        service.handlePause();
    }

    @Override
    public void resume() {
        service.handlePlay();
    }

    @Override
    public void play(@NotNull ContentStream stream, Integer startFrame, Integer endFrame) throws Exception {
        final String url;
        if (stream instanceof URLContentStream urlStream) {
            url = urlStream.getURL().toString();
            IOUtils.closeQuietly(stream);
        } else {
            String relativeUrl = service.context().media().createStreamUrl(stream, 60);
            url = service.context().hardware().getServerUrl() + relativeUrl;
        }
        service.playMedia("Notification", url,
                MP3.isCompatible((AudioFormat) stream.getStreamFormat()) ? MIME_TYPE_AUDIO_MPEG : MIME_TYPE_AUDIO_WAV);
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

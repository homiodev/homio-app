package org.homio.app.audio;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.EntityContext;
import org.homio.api.audio.AudioFormat;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.AudioSource;
import org.homio.api.audio.AudioStream;
import org.homio.api.audio.SelfContainedAudioSourceContainer;
import org.homio.api.audio.stream.FixedLengthAudioStream;
import org.homio.app.audio.javasound.JavaSoundAudioSink;
import org.homio.app.spring.ContextRefreshed;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class AudioService implements ContextRefreshed {

    private final Map<String, AudioStream> oneTimeStreams = new ConcurrentHashMap<>();
    private final Map<String, MultiTimeStreamContext> multiTimeStreams = new ConcurrentHashMap<>();
    // constructor parameters
    private final EntityContext entityContext;
    private final String defaultSink = JavaSoundAudioSink.class.getSimpleName();
    private Map<String, AudioSource> audioSources = Collections.emptyMap();
    private Map<String, SelfContainedAudioSourceContainer> selfContainedAudioContainers;
    @Getter
    private Map<String, AudioSink> audioSinks = Collections.emptyMap();

    @Override
    public void onContextRefresh() {
        this.audioSinks = entityContext.getBeansOfTypeWithBeanName(AudioSink.class);
        this.audioSources = entityContext.getBeansOfTypeWithBeanName(AudioSource.class);
        this.selfContainedAudioContainers = entityContext.getBeansOfTypeWithBeanName(SelfContainedAudioSourceContainer.class);
    }

    public Collection<SelfContainedAudioSourceContainer> getAudioSourceContainers() {
        return selfContainedAudioContainers.values();
    }

    /*public AudioSink getSink() {
        AudioSink sink = null;
        if (defaultSink != null) {
            sink = audioSinks.get(defaultSink);
            if (sink == null) {
                log.warn("Default AudioSink service '{}' not available!", defaultSink);
            }
        } else if (!audioSinks.isEmpty()) {
            sink = audioSinks.values().iterator().next();
        } else {
            log.debug("No AudioSink service available!");
        }
        return sink;
    }*/

    public void playRequested(String streamId, HttpServletResponse resp) throws IOException {
        // removeTimedOutStreams();

        try (InputStream stream = prepareInputStream(streamId, resp)) {
            if (stream == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else {
                IOUtils.copy(stream, resp.getOutputStream());
                resp.flushBuffer();
            }
        } catch (Exception ex) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public String createAudioUrl(FixedLengthAudioStream stream, int seconds) {
        String streamId = UUID.randomUUID().toString();
        multiTimeStreams.put(streamId, new MultiTimeStreamContext(stream, TimeUnit.SECONDS.toMillis(seconds)));
        return "rest/media/audio/" + streamId + "/play";
    }

    private InputStream prepareInputStream(final String streamId, final HttpServletResponse resp)
        throws Exception {
        final AudioStream stream;
        final boolean multiAccess;
        if (oneTimeStreams.containsKey(streamId)) {
            stream = oneTimeStreams.remove(streamId);
            multiAccess = false;
        } else if (multiTimeStreams.containsKey(streamId)) {
            stream = multiTimeStreams.get(streamId).stream;
            multiAccess = true;
        } else {
            throw new IllegalArgumentException("Unable to find audio with id: " + streamId);
        }

        log.debug("Stream to serve is {}", streamId);

        // try to set the content-type, if possible
        final String mimeType;
        if (AudioFormat.CODEC_MP3.equals(stream.getFormat().getCodec())) {
            mimeType = "audio/mpeg";
        } else if (AudioFormat.CONTAINER_WAVE.equals(stream.getFormat().getContainer())) {
            mimeType = "audio/wav";
        } else if (AudioFormat.CONTAINER_OGG.equals(stream.getFormat().getContainer())) {
            mimeType = "audio/ogg";
        } else {
            mimeType = null;
        }
        if (mimeType != null) {
            resp.setContentType(mimeType);
        }

        // try to set the content-length, if possible
        if (stream instanceof FixedLengthAudioStream) {
            long size = ((FixedLengthAudioStream) stream).length();
            resp.setContentLength((int) size);
        }

        if (multiAccess) {
            // we need to care about concurrent access and have a separate stream for each thread
            return ((FixedLengthAudioStream) stream).getClonedStream();
        } else {
            return stream;
        }
    }

    private synchronized void removeTimedOutStreams() {
        // Build list of expired streams.
        final long currentTimestamp = System.currentTimeMillis();
        multiTimeStreams.entrySet().removeIf(entry -> {
            MultiTimeStreamContext context = entry.getValue();
            boolean remove = context.date + context.timeoutMilliseconds > currentTimestamp;
            if (remove) {
                log.debug("Removed timed out stream <{}>", entry.getKey());
                try {
                    context.stream.close();
                } catch (IOException ignored) {
                }
            }
            return remove;
        });
    }

    @RequiredArgsConstructor
    private static class MultiTimeStreamContext {

        private final long date = System.currentTimeMillis();
        private final FixedLengthAudioStream stream;
        private final long timeoutMilliseconds;
    }
}

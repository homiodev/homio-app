package org.homio.addon.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@link OpenStreams} Keeps track of all open mjpeg streams so the byte[] can be given to all FIFO buffers to allow 1 to many streams without needing to
 * open more than 1 source stream.
 */
public class OpenStreams {

    private final List<StreamOutput> openStreams = Collections.synchronizedList(new ArrayList<>());
    public String boundary = "thisMjpegStream";

    public synchronized void addStream(StreamOutput stream) {
        openStreams.add(stream);
    }

    public synchronized void removeStream(StreamOutput stream) {
        openStreams.remove(stream);
    }

    public synchronized int getNumberOfStreams() {
        return openStreams.size();
    }

    public synchronized boolean isEmpty() {
        return openStreams.isEmpty();
    }

    public synchronized void updateContentType(String contentType, String boundary) {
        this.boundary = boundary;
        for (StreamOutput stream : openStreams) {
            stream.updateContentType(contentType);
        }
    }

    public synchronized void queueFrame(byte[] frame) {
        for (StreamOutput stream : openStreams) {
            stream.queueFrame(frame);
        }
    }

    public synchronized void closeAllStreams() {
        for (StreamOutput stream : openStreams) {
            stream.close();
        }
        openStreams.clear();
    }
}

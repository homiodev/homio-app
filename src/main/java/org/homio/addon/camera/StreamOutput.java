package org.homio.addon.camera;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The {@link StreamOutput} Streams mjpeg out to a client
 */
@Log4j2
@Getter
public class StreamOutput {

    private final HttpServletResponse response;
    private final String boundary;
    private String contentType;
    private final ServletOutputStream output;
    private final BlockingQueue<byte[]> fifo = new ArrayBlockingQueue<byte[]>(50);
    private boolean connected = false;
    private boolean isSnapshotBased = false;

    public StreamOutput(HttpServletResponse response) throws IOException {
        boundary = "thisMjpegStream";
        contentType = "multipart/x-mixed-replace; boundary=" + boundary;
        this.response = response;
        output = response.getOutputStream();
        isSnapshotBased = true;
    }

    public StreamOutput(HttpServletResponse response, String contentType) throws IOException {
        boundary = "";
        this.contentType = contentType;
        this.response = response;
        output = response.getOutputStream();
        if (!contentType.isEmpty()) {
            sendInitialHeaders();
            connected = true;
        }
    }

    public void sendSnapshotBasedFrame(byte[] currentSnapshot) throws IOException {
        String header = "--" + boundary + "\r\n" + "Content-Type: image/jpeg" + "\r\n" + "Content-Length: "
                + currentSnapshot.length + "\r\n\r\n";
        if (!connected) {
            sendInitialHeaders();
            // iOS needs to have two jpgs sent for the picture to appear instantly.
            output.write(header.getBytes());
            output.write(currentSnapshot);
            output.write("\r\n".getBytes());
            connected = true;
        }
        output.write(header.getBytes());
        output.write(currentSnapshot);
        output.write("\r\n".getBytes());
    }

    public void queueFrame(byte[] frame) {
        try {
            fifo.add(frame);
        } catch (IllegalStateException e) {
            log.debug("FIFO buffer has run out of space:{}", e.getMessage());
            fifo.remove();
            fifo.add(frame);
        }
    }

    public void updateContentType(String contentType) {
        this.contentType = contentType;
        if (!connected) {
            sendInitialHeaders();
            connected = true;
        }
    }

    public void sendFrame() throws IOException, InterruptedException {
        if (isSnapshotBased) {
            sendSnapshotBasedFrame(fifo.take());
        } else if (connected) {
            output.write(fifo.take());
        }
    }

    private void sendInitialHeaders() {
        response.setContentType(contentType);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
    }

    public void close() {
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }
}

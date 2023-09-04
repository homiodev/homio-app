package org.homio.addon.camera;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

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
    private final BlockingQueue<byte[]> fifo = new ArrayBlockingQueue<>(50);
    private boolean connected = false;
    private boolean isSnapshotBased = false;
    private @Setter @Nullable Consumer<byte[]> extraStreamConsumer;
    private @Setter boolean skipDuplicates = true;
    private byte[] lastFrame = new byte[0];

    public StreamOutput(HttpServletResponse response) throws IOException {
        this.boundary = "thisMjpegStream";
        this.contentType = "multipart/x-mixed-replace; boundary=" + boundary;
        this.response = response;
        this.output = response.getOutputStream();
        this.isSnapshotBased = true;
    }

    public StreamOutput(HttpServletResponse response, String contentType) throws IOException {
        this.boundary = "";
        this.contentType = contentType;
        this.response = response;
        this.output = response.getOutputStream();
        if (!contentType.isEmpty()) {
            sendInitialHeaders();
            this.connected = true;
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
            if (skipDuplicates && isSameFrame(frame)) {
                return;
            }
            lastFrame = frame;
            fifo.add(frame);
        } catch (IllegalStateException e) {
            log.debug("FIFO buffer has run out of space:{}", e.getMessage());
            fifo.remove();
            fifo.add(frame);
        }
    }

    @SneakyThrows
    private boolean isSameFrame(byte[] frame) {
        int[] pixels = getPixels(ImageIO.read(new ByteArrayInputStream(frame)));
        int[] pixels2 = getPixels(ImageIO.read(new ByteArrayInputStream(lastFrame)));
        return StreamOutput.equals(pixels, pixels2);
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

    private static boolean equals(final int[] data1, final int[] data2) {
        final int length = data1.length;
        if (length != data2.length) {
            log.debug("File lengths are different.");
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (data1[i] != data2[i]) {

                //If the alpha is 0 for both that means that the pixels are 100%
                //transparent and the color does not matter. Return false if
                //only 1 is 100% transparent.
                if ((((data1[i] >> 24) & 0xff) == 0) && (((data2[i] >> 24) & 0xff) == 0)) {
                    log.debug("Both pixles at spot {} are different but 100% transparent.", Integer.valueOf(i));
                } else {
                    log.debug("The pixel {} is different.", Integer.valueOf(i));
                    return false;
                }
            }
        }
        log.debug("Both groups of pixels are the same.");
        return true;
    }

    @SneakyThrows
    private static final int[] getPixels(final BufferedImage img) {

        final int width = img.getWidth();
        final int height = img.getHeight();
        int[] pixelData = new int[width * height];

        final Image pixelImg;
        if (img.getColorModel().getColorSpace() == ColorSpace.getInstance(ColorSpace.CS_sRGB)) {
            pixelImg = img;
        } else {
            pixelImg = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null).filter(img, null);
        }

        final PixelGrabber pg = new PixelGrabber(pixelImg, 0, 0, width, height, pixelData, 0, width);

        if (!pg.grabPixels()) {
            throw new RuntimeException();
        }

        return pixelData;
    }

    /**
     * Gets the {@link BufferedImage} from the passed in {@link File}.
     *
     * @param file The <code>File</code> to use.
     * @return The resulting <code>BufferedImage</code>
     */
    @SuppressWarnings("unused")
    final static BufferedImage getBufferedImage(final File file) {
        Image image;

        try (final FileInputStream inputStream = new FileInputStream(file)) {
            // ImageIO.read(file) is broken for some images so I went this
            // route
            image = Toolkit.getDefaultToolkit().createImage(file.getCanonicalPath());

            //forces the image to be rendered
            new ImageIcon(image);
        } catch (final Exception e2) {
            throw new RuntimeException(file.getPath(), e2);
        }

        final BufferedImage converted = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = converted.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return converted;
    }
}

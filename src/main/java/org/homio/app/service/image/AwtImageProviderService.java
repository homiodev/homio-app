package org.homio.app.service.image;

import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingFunction;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import org.homio.bundle.api.model.StylePosition;
import org.homio.bundle.api.service.ImageProviderService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

@Service
public class AwtImageProviderService implements ImageProviderService {

    public static final int TEXT_PADDING = 10;

    @Override
    public byte[] overlayImage(
            byte[] bgImage,
            byte[] fgImage,
            int x,
            int y,
            int width,
            int height,
            String formatType) {
        return handleChangeImage(
                bgImage,
                formatType,
                bgBufferedImage -> {
                    BufferedImage fgBufferedImage = ImageIO.read(new ByteArrayInputStream(fgImage));
                    Graphics2D g = bgBufferedImage.createGraphics();
                    g.setRenderingHint(
                            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawImage(bgBufferedImage, 0, 0, null);
                    g.drawImage(
                            fgBufferedImage,
                            x,
                            y,
                            width <= 0 ? fgBufferedImage.getWidth() : width,
                            height <= 0 ? fgBufferedImage.getHeight() : height,
                            null);
                    g.dispose();
                    return bgBufferedImage;
                });
    }

    @Override
    public byte[] cropImage(byte[] image, int x, int y, int width, int height, String formatType) {
        return handleChangeImage(
                image, formatType, bufferedImage -> bufferedImage.getSubimage(x, y, width, height));
    }

    @Override
    public byte[] setBrightness(byte[] image, float brightnessPercentage, String formatType) {
        if (brightnessPercentage == 1F) {
            return image;
        }
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    if (bufferedImage.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
                        bufferedImage =
                                copyImage(
                                        bufferedImage,
                                        "png".equals(formatType)
                                                ? BufferedImage.TYPE_INT_ARGB
                                                : BufferedImage.TYPE_INT_RGB);
                    }
                    BufferedImage targetImage =
                            copyImage(
                                    bufferedImage,
                                    "png".equals(formatType)
                                            ? BufferedImage.TYPE_INT_ARGB
                                            : BufferedImage.TYPE_INT_RGB);
                    int[] pixel = {0, 0, 0, 0};
                    float[] hsbvals = {0, 0, 0};
                    // recalculare every pixel, changing the brightness
                    for (int i = 0; i < targetImage.getHeight(); i++) {
                        for (int j = 0; j < targetImage.getWidth(); j++) {

                            // get the pixel data
                            targetImage.getRaster().getPixel(j, i, pixel);

                            // converts its data to hsb to change brightness
                            Color.RGBtoHSB(pixel[0], pixel[1], pixel[2], hsbvals);

                            // calculates the brightness component.
                            float newBrightness = hsbvals[2] * brightnessPercentage;
                            if (newBrightness > 1f) {
                                newBrightness = 1f;
                            }

                            // create a new color with the new brightness
                            Color c =
                                    new Color(
                                            Color.HSBtoRGB(hsbvals[0], hsbvals[1], newBrightness));

                            // set the new pixel
                            targetImage
                                    .getRaster()
                                    .setPixel(
                                            j,
                                            i,
                                            new int[] {
                                                c.getRed(), c.getGreen(), c.getBlue(), pixel[3]
                                            });
                        }
                    }
                    return targetImage;
                });
    }

    private BufferedImage copyImage(BufferedImage image, int type) {
        BufferedImage image2 = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D g = image2.createGraphics();
        g.drawRenderedImage(image, null);
        g.dispose();
        return image2;
    }

    @Override
    public byte[] flipImage(byte[] image, boolean flipVertically, String formatType) {
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    if (flipVertically) {
                        AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
                        tx.translate(0, -bufferedImage.getHeight());
                        AffineTransformOp op =
                                new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                        return op.filter(bufferedImage, null);
                    } else {
                        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
                        tx.translate(-bufferedImage.getWidth(), 0);
                        AffineTransformOp op =
                                new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                        return op.filter(bufferedImage, null);
                    }
                });
    }

    @Override
    public byte[] scaleImage(byte[] image, float scaleX, float scaleY, String formatType) {
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    int w = bufferedImage.getWidth();
                    int h = bufferedImage.getHeight();
                    BufferedImage after = createBufferedImage(w, h, bufferedImage, formatType);
                    AffineTransform at = new AffineTransform();
                    at.scale(scaleX, scaleY);
                    AffineTransformOp scaleOp =
                            new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
                    return scaleOp.filter(bufferedImage, after);
                });
    }

    private BufferedImage createBufferedImage(
            int w, int h, BufferedImage bufferedImage, String formatType) {
        if (bufferedImage.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
            return new BufferedImage(
                    w,
                    h,
                    BufferedImage.TYPE_BYTE_INDEXED,
                    (IndexColorModel) bufferedImage.getColorModel());
        } else if ("png".equals(formatType)) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    @Override
    public byte[] resizeImage(byte[] image, int width, int height, String formatType) {
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    int targetWidth =
                            width <= 0
                                    ? bufferedImage.getWidth() * bufferedImage.getHeight() / height
                                    : width;
                    int targetHeight =
                            height <= 0
                                    ? bufferedImage.getHeight() * bufferedImage.getWidth() / width
                                    : height;

                    BufferedImage resizedImage =
                            createBufferedImage(
                                    targetWidth, targetHeight, bufferedImage, formatType);
                    Graphics2D graphics2D = resizedImage.createGraphics();
                    graphics2D.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics2D.drawImage(bufferedImage, 0, 0, targetWidth, targetHeight, null);
                    return resizedImage;
                });
    }

    @Override
    @SneakyThrows
    public byte[] rotateImage(byte[] image, int angle, String formatType) {
        if (angle == 0 || angle % 360 == 0) {
            return image;
        }
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    double rads = Math.toRadians(angle);
                    double sin = Math.abs(Math.sin(rads));
                    double cos = Math.abs(Math.cos(rads));
                    double w =
                            Math.floor(
                                    bufferedImage.getWidth() * cos
                                            + bufferedImage.getHeight() * sin);
                    double h =
                            Math.floor(
                                    bufferedImage.getHeight() * cos
                                            + bufferedImage.getWidth() * sin);
                    BufferedImage rotatedImage =
                            createBufferedImage((int) w, (int) h, bufferedImage, formatType);
                    AffineTransform at = new AffineTransform();
                    at.translate(w / 2, h / 2);
                    at.rotate(rads, 0, 0);
                    at.translate(-bufferedImage.getWidth() / 2f, -bufferedImage.getHeight() / 2f);
                    AffineTransformOp rotateOp =
                            new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
                    rotateOp.filter(bufferedImage, rotatedImage);
                    return rotatedImage;
                });
    }

    @Override
    @SneakyThrows
    public byte[] translateImage(byte[] image, float tx, float ty, String formatType) {
        return handleChangeImage(
                image,
                formatType,
                bufferedImage -> {
                    double rads = Math.toRadians(90);
                    double sin = Math.abs(Math.sin(rads));
                    double cos = Math.abs(Math.cos(rads));
                    int w =
                            (int)
                                    Math.floor(
                                            bufferedImage.getWidth() * cos
                                                    + bufferedImage.getHeight() * sin);
                    int h =
                            (int)
                                    Math.floor(
                                            bufferedImage.getHeight() * cos
                                                    + bufferedImage.getWidth() * sin);
                    BufferedImage rotatedImage =
                            createBufferedImage(w, h, bufferedImage, formatType);
                    AffineTransform at = new AffineTransform();
                    at.translate(tx, ty);
                    return rotatedImage;
                });
    }

    @Override
    @SneakyThrows
    public byte[] addText(
            byte[] image,
            StylePosition stylePosition,
            String color,
            String text,
            String formatType) {
        return handleImage(
                image,
                formatType,
                (g, bufferedImage) -> {
                    g.setFont(new Font("TimesRoman", Font.BOLD, 30));
                    g.setColor(Color.decode(color));
                    Pair<Integer, Integer> position =
                            getTextPosition(g, bufferedImage, text, stylePosition);
                    if (position != null) {
                        g.drawString(text, position.getFirst(), position.getSecond());
                        return true;
                    }
                    return false;
                });
    }

    @SneakyThrows
    private byte[] handleImage(
            byte[] content,
            String formatType,
            ThrowingBiFunction<Graphics2D, BufferedImage, Boolean, Exception> handle) {
        BufferedImage newBi = ImageIO.read(new ByteArrayInputStream(content));
        Graphics2D g = newBi.createGraphics();
        try {
            if (handle.apply(g, newBi)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageIO.write(newBi, formatType, outputStream);
                return outputStream.toByteArray();
            }
        } finally {
            g.dispose();
        }
        return content;
    }

    @SneakyThrows
    private byte[] handleChangeImage(
            byte[] content,
            String formatType,
            ThrowingFunction<BufferedImage, BufferedImage, Exception> handle) {
        BufferedImage newBi = ImageIO.read(new ByteArrayInputStream(content));
        BufferedImage changedImage = handle.apply(newBi);
        if (changedImage != null) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(changedImage, formatType, outputStream);
            return outputStream.toByteArray();
        }
        return content;
    }

    private Pair<Integer, Integer> getTextPosition(
            Graphics2D g, BufferedImage bufferedImage, String text, StylePosition stylePosition) {
        int imageWidth = bufferedImage.getWidth();
        int imageHeight = bufferedImage.getHeight();
        int textWidth = g.getFontMetrics().stringWidth(text);
        int textHeight = g.getFontMetrics().getHeight();
        int right = imageWidth - textWidth - TEXT_PADDING;
        int center = (imageWidth + textWidth) / 2;
        int middle = (imageHeight + textHeight) / 2;
        int bottom = imageHeight - textHeight - TEXT_PADDING;
        switch (stylePosition) {
            case None:
                return null;
            case TopLeft:
                return Pair.of(TEXT_PADDING, TEXT_PADDING);
            case TopRight:
                return Pair.of(right, TEXT_PADDING);
            case BottomLeft:
                return Pair.of(TEXT_PADDING, bottom);
            case BottomRight:
                return Pair.of(right, bottom);
            case BottomCenter:
                return Pair.of(center, bottom);
            case TopCenter:
                return Pair.of(center, TEXT_PADDING);
            case MiddleCenter:
                return Pair.of(center, middle);
            case MiddleLeft:
                return Pair.of(TEXT_PADDING, middle);
            case MiddleRight:
                return Pair.of(right, middle);
        }
        return null;
    }
}

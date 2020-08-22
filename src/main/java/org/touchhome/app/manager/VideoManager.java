package org.touchhome.app.manager;

import com.github.sarxos.webcam.Webcam;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.touchhome.app.manager.ImageManager.imagesDir;

@Log4j2
@Component
public class VideoManager {

    public boolean isCameraDetected() {
        return Webcam.getDefault() != null;
    }

    public String getCameraName() {
        return Webcam.getDefault().getName();
    }

    public Path takeSnapshot(int width, int height) throws IOException {
        Webcam webcam = Webcam.getDefault();
        try {
            //  webcam.getViewSizes()
            webcam.setViewSize(new Dimension(width, height));
            webcam.open();
            BufferedImage image = webcam.getImage();
            // save image to PNG file
            Path imagePath = imagesDir.resolve("CameraImage_" + TouchHomeUtils.getTimestampString() + ".png");
            ImageIO.write(image, "PNG", imagePath.toFile());
            return imagePath;
        } finally {
            webcam.close();
        }
    }

    public void startVideo(int width, int height, Boolean saveToFile) {
        Webcam webcam = Webcam.getDefault();
        webcam.setViewSize(new Dimension(width, height));
        webcam.open();
    }


    public void stopVideo() {
    }

    public Boolean isStreamVideo() {
        return null;
    }
}











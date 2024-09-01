package org.homio.addon.fs;

import co.elastic.thumbnails4j.core.Dimensions;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

@Log4j2
public class Mp3Thumbnailer extends FileRequireThumbnailer {

    @SneakyThrows
    @Override
    protected BufferedImage getThumbnail(File input, Dimensions dimensions) {
        Mp3File song = new Mp3File(input);
        if (song.hasId3v2Tag()) {
            ID3v2 id3v2tag = song.getId3v2Tag();
            byte[] imageData = id3v2tag.getAlbumImage();
            return getScaledBI(ImageIO.read(new ByteArrayInputStream(imageData)), dimensions);
        }
        return null;
    }

    private BufferedImage getScaledBI(BufferedImage org, Dimensions dimensions) {
        Image tmp = org.getScaledInstance(dimensions.getWidth(), dimensions.getHeight(), Image.SCALE_SMOOTH);
        BufferedImage scaleBI = new BufferedImage(dimensions.getWidth(), dimensions.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = scaleBI.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return scaleBI;
    }
}

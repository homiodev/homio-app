package org.homio.addon.fs;

import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.Thumbnailer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FileRequireThumbnailer implements Thumbnailer  {

    public static Logger log = LogManager.getLogger(FileRequireThumbnailer.class);

    @Override
    public final List<BufferedImage> getThumbnails(File input, List<Dimensions> dimensions) {
        return List.of(getThumbnail(input, dimensions.iterator().next()));
    }

    @SneakyThrows
    protected BufferedImage getThumbnail(File input, Dimensions dimensions) {
        return getThumbnail(new FileInputStream(input), dimensions);
    }

    @Override
    public final List<BufferedImage> getThumbnails(InputStream inputStream, List<Dimensions> dimensions) {
        return List.of(getThumbnail(inputStream, dimensions.iterator().next()));
    }

    protected BufferedImage getThumbnail(InputStream inputStream, Dimensions dimensions) {
        throw new IllegalStateException("Unable to evaluate tag image from input stream");
    }
}

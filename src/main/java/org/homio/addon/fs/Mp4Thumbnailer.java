package org.homio.addon.fs;

import co.elastic.thumbnails4j.core.Dimensions;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;

@Log4j2
public class Mp4Thumbnailer extends FileRequireThumbnailer {

  @SneakyThrows
  @Override
  protected BufferedImage getThumbnail(File input, Dimensions dimensions) {
    String ffmpegCommand = "%s -i %s -ss 00:00:01.000 -vf scale=%d:%d -q:v 1 -vframes 1 -f image2pipe pipe:1"
      .formatted(FFMPEG_LOCATION, input.toString(), dimensions.getWidth(), dimensions.getHeight());
    ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
    Process process = processBuilder.start();
    try {
      // Capture the input stream of the FFmpeg process
      byte[] image = IOUtils.toByteArray(process.getInputStream());
      return ImageIO.read(new ByteArrayInputStream(image));
    } finally {
      process.destroy();
    }
  }
}

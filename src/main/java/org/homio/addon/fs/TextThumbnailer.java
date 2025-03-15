package org.homio.addon.fs;

import co.elastic.thumbnails4j.core.Dimensions;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Log4j2
public class TextThumbnailer extends FileRequireThumbnailer {

  @SneakyThrows
  @Override
  protected BufferedImage getThumbnail(InputStream inputStream, Dimensions dimensions) {
    String text;
    try (InputStream stream = inputStream) {
      text = IOUtils.toString(stream, StandardCharsets.UTF_8);
    }

    BufferedImage img = new BufferedImage(dimensions.getWidth(), dimensions.getHeight(), BufferedImage.TYPE_INT_ARGB);

    Graphics2D graphics = img.createGraphics();

    Font font = new Font("Arial", Font.PLAIN, 11);
    graphics.setFont(font);

    graphics.dispose();

    img = new BufferedImage(dimensions.getWidth(), dimensions.getHeight(), BufferedImage.TYPE_INT_ARGB);
    graphics = img.createGraphics();
    graphics.setPaint(Color.WHITE);
    graphics.fillRect(0, 0, dimensions.getWidth(), dimensions.getHeight());
    graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    graphics.setFont(font);
    FontMetrics fm = graphics.getFontMetrics();
    graphics.setColor(Color.BLACK);

    int textW = graphics.getFontMetrics().stringWidth(text);

    int lineCount = Math.max(1, textW / dimensions.getWidth());

    int cc = text.length() / lineCount;

    int index = 0;
    ArrayList<String> lines = new ArrayList<>();

    while (index < text.length()) {
      String sub = text.substring(index, Math.min(index + cc, text.length()));
      lines.add(sub);
      index += cc;
    }

    int y = fm.getAscent();
    for (String line : lines) {
      y += graphics.getFontMetrics().getHeight();
      graphics.drawString(line, 0, y);
    }

    graphics.dispose();

    return img;
  }
}

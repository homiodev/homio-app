package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.util.CommonUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.awt.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

@Log4j2
@RestController
@RequestMapping("/rest/resource")
@RequiredArgsConstructor
public class ResourceController {

  @SneakyThrows
  private static List<Path> getFontFiles() {
    Path fonts = CommonUtils.createDirectoriesIfNotExists(CommonUtils.getConfigPath().resolve("fonts"));
    try (Stream<Path> walk = Files.walk(fonts, 1)) {
      return walk
        .filter(p -> !Files.isDirectory(p))
        .filter(f -> f.getFileName().toString().endsWith(".ttf"))
        .toList();
    }
  }

  @SneakyThrows
  @GetMapping(value = "/fonts", produces = "text/css; charset=utf-8")
  public String getFonts() {
    StringBuilder css = new StringBuilder();
    for (Path fontFile : getFontFiles()) {
      Font trueFont = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile());
      css.append("""
        @font-face {
          font-family: '%s';
          font-style: normal;
          font-weight: 400;
          font-display: swap;
          src: url($DEVICE_URL/rest/resource/font/%s) format('truetype');
        }
        """.formatted(trueFont.getFamily(), fontFile.getFileName()));
    }
    return css.toString();
  }

  @GetMapping(value = "/font/{name}", produces = "text/css; charset=utf-8")
  public ResponseEntity<StreamingResponseBody> getFont(@PathVariable("name") String name) {
    Path fonts = CommonUtils.createDirectoriesIfNotExists(CommonUtils.getConfigPath().resolve("fonts"));
    return new ResponseEntity<>(
      outputStream -> {
        try (FileChannel inChannel = FileChannel.open(fonts.resolve(name), StandardOpenOption.READ)) {
          long size = inChannel.size();
          WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
          inChannel.transferTo(0, size, writableByteChannel);
        }
      },
      HttpStatus.OK);
  }
}

package org.homio.addon.media;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.Context;
import org.homio.api.model.StylePosition;
import org.homio.api.state.RawType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.ArgumentType;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.setting.workspace.ImageDefaultProcessingSetting;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.BiFunction;

@Log4j2
@Getter
@Component
public class Scratch3ImageEditBlocks extends Scratch3ExtensionBlocks {

  private static final String IMAGE = "IMAGE";
  private final MenuBlock.StaticMenuBlock<StylePosition> textPositionMenu;

  public Scratch3ImageEditBlocks(Context context) {
    super("#3C6360", context, null, "imageeditor");
    setParent(ScratchParent.media);
    // Menu
    this.textPositionMenu = menuStatic("textPositionMenu", StylePosition.class, StylePosition.TopLeft);

    // Blocks
    blockReporter(11, "rotate", "Rotate at [DEGREE]° of image [IMAGE]",
      this::rotateImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("DEGREE", 90);
      });

    blockReporter(22, "scale", "Scale at SX[SX]XY[SY] of image [IMAGE]",
      this::scaleImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("SX", 0.5f);
        block.addArgument("SY", 0.5f);
      });

    blockReporter(33, "resize", "Resize at W[W]H[H] of image [IMAGE]",
      this::resizeImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("W", 640);
        block.addArgument("H", 480);
      });

    blockReporter(44, "translate", "Translate at X[X]Y[Y] of image [IMAGE]",
      this::translateImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("X", 50);
        block.addArgument("Y", 50);
      });

    blockReporter(55, "text", "Set text [TEXT] with color [COLOR] at pos [POSITION] of image [IMAGE]",
      this::setTextImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("TEXT", "sample text");
        block.addArgument("POSITION", this.textPositionMenu);
        block.addArgument("COLOR", ArgumentType.color, "#BA653A");
      });

    blockReporter(66, "flip", "Flip image [IMAGE] | Vertically: [VALUE]",
      this::flipImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument(VALUE, false);
      });

    blockReporter(77, "crop", "Crop at X[X]Y[Y]W[W]H[H] of image [IMAGE]",
      this::cropImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("X", 0);
        block.addArgument("Y", 0);
        block.addArgument("W", -1);
        block.addArgument("H", -1);
      });

    blockReporter(88, "overlay", "Overlay image [IMAGE] with [IMAGE2](X[X]Y[Y]W[W]H[H])",
      this::overlayImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument("IMAGE2", "");
        block.addArgument("X", 0);
        block.addArgument("Y", 0);
        block.addArgument("W", -1);
        block.addArgument("H", -1);
      });

    blockReporter(99, "brightness", "Set brightness [VALUE] of image [IMAGE])",
      this::brightnessImageCommand,
      block -> {
        block.addArgument("IMAGE", "");
        block.addArgument(VALUE, 1);
      });
  }

  private RawType brightnessImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .setBrightness(image, workspaceBlock.getInputFloat(VALUE), formatType));
  }

  private RawType overlayImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting().getValue(ImageDefaultProcessingSetting.class)
      .overlayImage(image,
        fixImage(workspaceBlock.getInputRawType("IMAGE2")).getValue(),
        workspaceBlock.getInputInteger("X"),
        workspaceBlock.getInputInteger("Y"),
        workspaceBlock.getInputInteger("W"),
        workspaceBlock.getInputInteger("H"),
        formatType));
  }

  private RawType cropImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .cropImage(
        image,
        workspaceBlock.getInputInteger("X"),
        workspaceBlock.getInputInteger("Y"),
        workspaceBlock.getInputInteger("W"),
        workspaceBlock.getInputInteger("H"),
        formatType));
  }

  private RawType flipImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .flipImage(image, workspaceBlock.getInputBoolean(VALUE), formatType));
  }

  private RawType scaleImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .scaleImage(image, workspaceBlock.getInputInteger("SX"), workspaceBlock.getInputInteger("SY"), formatType));
  }

  private RawType resizeImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .resizeImage(image, workspaceBlock.getInputInteger("W"), workspaceBlock.getInputInteger("H"), formatType));
  }

  private RawType translateImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .translateImage(image, workspaceBlock.getInputFloat("X"), workspaceBlock.getInputFloat("Y"), formatType));
  }

  private RawType setTextImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .addText(image, workspaceBlock.getMenuValue("POSITION", this.textPositionMenu), workspaceBlock.getInputString("COLOR"),
        workspaceBlock.getInputString("TEXT"), formatType));
  }

  private RawType rotateImageCommand(WorkspaceBlock workspaceBlock) {
    return handle(workspaceBlock, (formatType, image) -> context
      .setting()
      .getValue(ImageDefaultProcessingSetting.class)
      .rotateImage(image, workspaceBlock.getInputInteger("DEGREE"), formatType));
  }

  @SneakyThrows
  private RawType handle(
    WorkspaceBlock workspaceBlock, BiFunction<String, byte[], byte[]> handleFn) {
    RawType image = workspaceBlock.getInputRawType(IMAGE);
    Pair<Boolean, byte[]> pair = fixImage(image);
    String formatType = Objects.toString(image.getMimeType(), "png");
    if (formatType.startsWith("image/")) {
      formatType = formatType.substring("image/".length());
    }
    if (pair.getKey()) {
      byte[] convertedValue = handleFn.apply(formatType, pair.getValue());
      String encodedValue =
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(convertedValue);
      return new RawType(encodedValue.getBytes(), image.getMimeType(), image.getName());
    } else {
      return new RawType(handleFn.apply(formatType, pair.getValue()), image.getMimeType(), image.getName());
    }
  }

  @SneakyThrows
  private Pair<Boolean, byte[]> fixImage(RawType image) {
    if (image == null) {
      throw new RuntimeException("Unable to fetch image from block");
    }
    if (image.startsWith("data:")) {
      String imageAsString = new String(image.byteArrayValue());
      String[] urlParts = imageAsString.split(",");
      if (urlParts.length > 1) {
        imageAsString = urlParts[1];
      }
      InputStream decodedInputStream = Base64.getDecoder()
        .wrap(new ByteArrayInputStream(imageAsString.getBytes(StandardCharsets.UTF_8)));
      return Pair.of(true, IOUtils.toByteArray(decodedInputStream));
    }
    return Pair.of(false, image.byteArrayValue());
  }
}

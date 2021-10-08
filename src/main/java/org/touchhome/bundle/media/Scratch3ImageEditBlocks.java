package org.touchhome.bundle.media;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.workspace.ImageDefaultProcessingSetting;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.StylePosition;
import org.touchhome.bundle.api.state.RawType;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.ArgumentType;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BiFunction;

@Log4j2
@Getter
@Component
public class Scratch3ImageEditBlocks extends Scratch3ExtensionBlocks {

    private static final String IMAGE = "IMAGE";
    private final MenuBlock.StaticMenuBlock<StylePosition> textPositionMenu;

    private final Scratch3Block rotateImageCommand;
    private final Scratch3Block scaleImageCommand;
    private final Scratch3Block resizeImageCommand;
    private final Scratch3Block translateImageCommand;
    private final Scratch3Block setTextImageCommand;
    private final Scratch3Block flipImageCommand;
    private final Scratch3Block cropImageCommand;
    private final Scratch3Block overlayImageCommand;
    private final Scratch3Block brightnessImageCommand;

    public Scratch3ImageEditBlocks(EntityContext entityContext) {
        super("#3C6360", entityContext, null, "imageeditor");
        setParent("media");
        // Menu
        this.textPositionMenu = MenuBlock.ofStatic("textPositionMenu", StylePosition.class, StylePosition.TopLeft);

        // Blocks
        this.rotateImageCommand = img(11, "rotate", "Rotate at [DEGREE]° of image [IMAGE]", this::rotateImageCommand);
        this.rotateImageCommand.addArgument("DEGREE", 90);

        this.scaleImageCommand = img(22, "scale", "Scale at SX[SX]XY[SY] of image [IMAGE]", this::scaleImageCommand);
        this.scaleImageCommand.addArgument("SX", 0.5f);
        this.scaleImageCommand.addArgument("SY", 0.5f);

        this.resizeImageCommand = img(33, "resize", "Resize at W[W]H[H] of image [IMAGE]", this::resizeImageCommand);
        this.resizeImageCommand.addArgument("W", 640);
        this.resizeImageCommand.addArgument("H", 480);

        this.translateImageCommand = img(44, "translate", "Translate at X[X]Y[Y] of image [IMAGE]", this::translateImageCommand);
        this.translateImageCommand.addArgument("X", 50);
        this.translateImageCommand.addArgument("Y", 50);

        this.setTextImageCommand = img(55, "text",
                "Set text [TEXT] with color [COLOR] at pos [POSITION] of image [IMAGE]", this::setTextImageCommand);
        this.setTextImageCommand.addArgument("TEXT", "sample text");
        this.setTextImageCommand.addArgument("POSITION", this.textPositionMenu);
        this.setTextImageCommand.addArgument("COLOR", ArgumentType.color, "#BA653A");

        this.flipImageCommand = img(66, "flip", "Flip image [IMAGE] | Vertically: [VALUE]", this::flipImageCommand);
        this.flipImageCommand.addArgument(VALUE, false);

        this.cropImageCommand = img(77, "crop", "Crop at X[X]Y[Y]W[W]H[H] of image [IMAGE]", this::cropImageCommand);
        this.cropImageCommand.addArgument("X", 0);
        this.cropImageCommand.addArgument("Y", 0);
        this.cropImageCommand.addArgument("W", -1);
        this.cropImageCommand.addArgument("H", -1);

        this.overlayImageCommand = img(88, "overlay", "Overlay image [IMAGE] with [IMAGE2](X[X]Y[Y]W[W]H[H])",
                this::overlayImageCommand);
        this.overlayImageCommand.addArgument("IMAGE2", "");
        this.overlayImageCommand.addArgument("X", 0);
        this.overlayImageCommand.addArgument("Y", 0);
        this.overlayImageCommand.addArgument("W", -1);
        this.overlayImageCommand.addArgument("H", -1);

        this.brightnessImageCommand = img(99, "brightness", "Set brightness [VALUE] of image [IMAGE])",
                this::brightnessImageCommand);
        this.brightnessImageCommand.addArgument(VALUE, 1);
    }

    private RawType brightnessImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).setBrightness(image,
                        workspaceBlock.getInputFloat(VALUE), formatType));
    }

    private RawType overlayImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).overlayImage(image,
                        fixImage(workspaceBlock.getInputRawType("IMAGE2")).getSecond(),
                        workspaceBlock.getInputInteger("X"),
                        workspaceBlock.getInputInteger("Y"),
                        workspaceBlock.getInputInteger("W"),
                        workspaceBlock.getInputInteger("H"), formatType));
    }

    private RawType cropImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).cropImage(image,
                        workspaceBlock.getInputInteger("X"),
                        workspaceBlock.getInputInteger("Y"),
                        workspaceBlock.getInputInteger("W"),
                        workspaceBlock.getInputInteger("H"), formatType));
    }

    private RawType flipImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).flipImage(image,
                        workspaceBlock.getInputBoolean(VALUE), formatType));
    }

    private RawType scaleImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).scaleImage(image,
                        workspaceBlock.getInputInteger("SX"),
                        workspaceBlock.getInputInteger("SY"), formatType));
    }

    private RawType resizeImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).resizeImage(image,
                        workspaceBlock.getInputInteger("W"),
                        workspaceBlock.getInputInteger("H"), formatType));
    }

    private RawType translateImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).translateImage(image,
                        workspaceBlock.getInputFloat("X"),
                        workspaceBlock.getInputFloat("Y"), formatType));
    }

    private RawType setTextImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).addText(image,
                        workspaceBlock.getMenuValue("POSITION", this.textPositionMenu),
                        workspaceBlock.getInputString("COLOR"),
                        workspaceBlock.getInputString("TEXT"), formatType));
    }

    private RawType rotateImageCommand(WorkspaceBlock workspaceBlock) {
        return handle(workspaceBlock, (formatType, image) ->
                entityContext.setting().getValue(ImageDefaultProcessingSetting.class).rotateImage(image,
                        workspaceBlock.getInputInteger("DEGREE"), formatType));
    }

    @SneakyThrows
    private RawType handle(WorkspaceBlock workspaceBlock, BiFunction<String, byte[], byte[]> handleFn) {
        RawType image = workspaceBlock.getInputRawType(IMAGE);
        Pair<Boolean, byte[]> pair = fixImage(image);
        String formatType = StringUtils.defaultString(image.getMimeType(), "png");
        if (formatType.startsWith("image/")) {
            formatType = formatType.substring("image/".length());
        }
        if (pair.getFirst()) {
            byte[] convertedValue = handleFn.apply(formatType, pair.getSecond());
            String encodedValue = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(convertedValue);
            return new RawType(encodedValue.getBytes(), image.getMimeType(), image.getName());
        } else {
            return new RawType(handleFn.apply(formatType, pair.getSecond()), image.getMimeType(), image.getName());
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
            InputStream decodedInputStream = Base64.getDecoder().wrap(new ByteArrayInputStream(imageAsString.getBytes(StandardCharsets.UTF_8)));
            return Pair.of(true, IOUtils.toByteArray(decodedInputStream));
        }
        return Pair.of(false, image.byteArrayValue());
    }

    private Scratch3Block img(int order, String opcode, String text, Scratch3Block.Scratch3BlockEvaluateHandler evalHandler) {
        Scratch3Block scratch3Block = Scratch3Block.ofReporter(order, opcode, text, evalHandler);
        scratch3Block.addArgument("IMAGE", "");
        return scratch3Block;
    }
}

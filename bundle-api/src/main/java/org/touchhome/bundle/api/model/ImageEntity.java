package org.touchhome.bundle.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Entity
@Getter
@Setter
public class ImageEntity extends BaseEntity<ImageEntity> {

    private static final Pattern TRANSLATE = Pattern.compile(".*translate(\\d+,\\d+)");
    @Column
    private String path;
    @Column(nullable = false)
    private Integer originalWidth;
    @Column(nullable = false)
    private Integer originalHeight;
    @Column(nullable = false)
    private String color;
    @Column
    private String fileSystem;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ImageType imageType = ImageType.PNG;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserType userType = UserType.CommonType;
    @Lob
    @Column(length = 1048576)
    @JsonIgnore
    private byte[] blob;

    public static String translate(String value, Integer x, Integer y) {
        return value.replaceAll("translate\\(\\d+,\\d+\\)", "translate(" + x + "," + y + ")");
    }

    @SneakyThrows
    public byte[] getBlob() {
        return path == null ? blob : Files.readAllBytes(toPath());
    }

    @JsonIgnore
    public String getBlobAsString() {
        return new String(getBlob());
    }

    @SneakyThrows
    public Path toPath() {
        if (fileSystem != null) {
            return TouchHomeUtils.getOrCreateNewFileSystem(fileSystem).getPath(path);
        }
        return Paths.get(path);
    }

    public String getMimeType() {
        return getEntityID().length() > 3 ? ("image/" + getEntityID().substring(getEntityID().length() - 3)) : "";
    }

    /*public String getSvgImageAsString(DevicePlugin devicePlugin, Integer width, Integer height, Float scale) {
        scale = scale == null ? 1 : scale;
        int imageWidth = 0;
        int imageHeight = 0;
        if (devicePlugin != null) {
            imageWidth = devicePlugin.getDesireWidth();
            imageHeight = devicePlugin.getDesireHeight();
        }

        if (width != null) {
            imageWidth = width;
        }
        if (height != null) {
            imageHeight = height;
        }

        if (imageWidth == 0) {
            imageWidth = originalWidth;
        }

        if (imageHeight == 0) {
            imageHeight = originalHeight;
        }

        StringBuilder builder = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"");
        builder.append(imageWidth * scale).append("px\" height=\"").append(imageHeight * scale).append("px\" viewBox=\"0 0 ")
                .append(originalWidth).append(" ").append(originalHeight).append("\">");

        builder.append(getBlobAsString());
        builder.append("</svg>");

        return builder.toString();
    }*/

    public enum ImageType {
        PNG, SVG
    }

    public enum UserType {
        OutputType, InputType, CommonType, WidgetTemplate
    }
}

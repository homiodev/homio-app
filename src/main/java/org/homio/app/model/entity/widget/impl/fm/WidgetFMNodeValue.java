package org.homio.app.model.entity.widget.impl.fm;

import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.Thumbnailer;
import co.elastic.thumbnails4j.doc.DOCThumbnailer;
import co.elastic.thumbnails4j.docx.DOCXThumbnailer;
import co.elastic.thumbnails4j.image.ImageThumbnailer;
import co.elastic.thumbnails4j.pdf.PDFThumbnailer;
import co.elastic.thumbnails4j.pptx.PPTXThumbnailer;
import co.elastic.thumbnails4j.xls.XLSThumbnailer;
import co.elastic.thumbnails4j.xlsx.XLSXThumbnailer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.fs.FileRequireThumbnailer;
import org.homio.addon.fs.Mp3Thumbnailer;
import org.homio.addon.fs.Mp4Thumbnailer;
import org.homio.addon.fs.TextThumbnailer;
import org.homio.api.fs.TreeNode;
import org.homio.app.service.device.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.*;

@Log4j2
public record WidgetFMNodeValue(TreeNode treeNode) {

    private static final LoadingCache<ThumbnailRequest, String> thumbnailCache =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {
                @SneakyThrows
                public @NotNull String load(@NotNull ThumbnailRequest request) {
                    List<Dimensions> outputDimensions = List.of(new Dimensions(request.width, request.height));
                    BufferedImage output = getBufferedImage(request.thumbnailer, request.treeNode, outputDimensions);
                    if (output != null) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        try (OutputStream b64 = new Base64OutputStream(os)) {
                            ImageIO.write(output, "png", b64);
                        }
                        return os.toString();
                    }
                    return "";
                }
            });

    public static String getThumbnail(TreeNode treeNode, int width, int height, boolean drawTextAsImage) throws IOException {
        String contentType = StringUtils.defaultString(treeNode.getAttributes().getContentType(), "text/plain");
        if (contentType.startsWith("text/")
                || contentType.equals("application/javascript")
                || contentType.equals(APPLICATION_JSON_VALUE)) {
            if (!drawTextAsImage) {
                try (InputStream stream = treeNode.getInputStream()) {
                    return IOUtils.toString(stream, StandardCharsets.UTF_8);
                }
            }
        }
        Thumbnailer thumbnailer = buildThumbnail(contentType);
        try {
            String content = thumbnailCache.get(new ThumbnailRequest(treeNode, thumbnailer, width, height));
            return StringUtils.isEmpty(content) ? "" : "data:image/png;base64," + content;
        } catch (Exception ex) {
            log.debug("Unable to fetch thumbnail from file: <{}>", treeNode.getName());
        }
        return "";
    }

    @SneakyThrows
    private static @Nullable BufferedImage getBufferedImage(Thumbnailer thumbnailer, TreeNode treeNode, List<Dimensions> outputDimensions) {
        List<BufferedImage> images = null;
        if (thumbnailer instanceof FileRequireThumbnailer) {
            if (treeNode.getFileSystem() instanceof LocalFileSystemProvider fileProvider) {
                File file = fileProvider.getFile(treeNode.getId());
                images = thumbnailer.getThumbnails(file, outputDimensions);
            }
        } else {
            try (InputStream stream = treeNode.getInputStream()) {
                images = thumbnailer.getThumbnails(stream, outputDimensions);
            }
        }
        return images != null && !images.isEmpty() ? images.get(0) : null;
    }

    private static Thumbnailer buildThumbnail(String contentType) {
        return switch (contentType) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> new DOCXThumbnailer();
            case "application/msword" -> new DOCThumbnailer();
            case "audio/mp3", "audio/mp4", "audio/vnd.wav", "audio/mpeg" -> new Mp3Thumbnailer();
            case "video/mp4" -> new Mp4Thumbnailer();
            case "image/jpg", "image/gif", IMAGE_JPEG_VALUE, IMAGE_PNG_VALUE -> new ImageThumbnailer("png");
            case APPLICATION_PDF_VALUE -> new PDFThumbnailer();
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> new PPTXThumbnailer();
            case "application/vnd.ms-excel" -> new XLSThumbnailer();
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> new XLSXThumbnailer();
            default -> new TextThumbnailer();
        };
    }

    private record ThumbnailRequest(@NotNull TreeNode treeNode, @NotNull Thumbnailer thumbnailer, int width,
                                    int height) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ThumbnailRequest that = (ThumbnailRequest) o;

            if (width != that.width) {
                return false;
            }
            if (height != that.height) {
                return false;
            }
            return Objects.equals(treeNode.getId(), that.treeNode.getId());
        }

        @Override
        public int hashCode() {
            int result = Objects.requireNonNull(treeNode.getId()).hashCode();
            result = 31 * result + width;
            result = 31 * result + height;
            return result;
        }
    }
}

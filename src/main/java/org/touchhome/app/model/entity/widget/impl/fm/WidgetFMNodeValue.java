package org.touchhome.app.model.entity.widget.impl.fm;

import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.Thumbnailer;
import co.elastic.thumbnails4j.doc.DOCThumbnailer;
import co.elastic.thumbnails4j.docx.DOCXThumbnailer;
import co.elastic.thumbnails4j.image.ImageThumbnailer;
import co.elastic.thumbnails4j.pdf.PDFThumbnailer;
import co.elastic.thumbnails4j.pptx.PPTXThumbnailer;
import co.elastic.thumbnails4j.xls.XLSThumbnailer;
import co.elastic.thumbnails4j.xlsx.XLSXThumbnailer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.touchhome.common.fs.TreeNode;

@Log4j2
@Getter
public class WidgetFMNodeValue {

    private final TreeNode treeNode;
    private String content;
    private ResolveContentType resolveType = ResolveContentType.unknown;

    @SneakyThrows
    public WidgetFMNodeValue(TreeNode treeNode, int width, int height) {
        this.treeNode = treeNode;
        List<Dimensions> outputDimensions =
            Collections.singletonList(new Dimensions(width, height));

        String contentType = treeNode.getAttributes().getContentType();
        if (contentType != null) {
            Thumbnailer thumbnailer = buildThumbnail(contentType);

            if (thumbnailer != null) {
                try {
                    try (InputStream stream = treeNode.getInputStream()) {
                        BufferedImage output =
                            thumbnailer.getThumbnails(stream, outputDimensions).get(0);
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        OutputStream b64 = new Base64OutputStream(os);
                        ImageIO.write(output, "png", b64);
                        b64.close();
                        this.content = os.toString();
                        this.resolveType = ResolveContentType.image;
                    }
                } catch (Exception ex) {
                    log.debug("Unable to fetch thumbnail from file: <{}>", treeNode.getName());
                }
                // String encodedValue = "data:image/jpeg;base64," +
                // Base64.getEncoder().encodeToString(convertedValue);
            } else if (contentType.startsWith("text/")
                || contentType.equals("application/javascript")
                || contentType.equals("application/json")) {
                if (treeNode.getAttributes().getSize() <= FileUtils.ONE_MB) {
                    try (InputStream stream = treeNode.getInputStream()) {
                        this.content = IOUtils.toString(stream, StandardCharsets.UTF_8);
                        this.resolveType = ResolveContentType.text;
                    }
                }
            } else {
                switch (contentType) {
                    case "audio/mpeg":
                    case "video/mp4":
                        break;
                }
            }
        } else {
            log.debug("Unable to find contentType for file: <{}>", treeNode.getName());
        }
    }

    @Override
    public int hashCode() {
        return treeNode.hashCode();
    }

    private Thumbnailer buildThumbnail(String contentType) {
        switch (contentType) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return new DOCXThumbnailer();
            case "application/msword":
                return new DOCThumbnailer();
            case "image/jpeg":
            case "image/gif":
            case "image/png":
                return new ImageThumbnailer("png");
            case "application/pdf":
                return new PDFThumbnailer();
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                return new PPTXThumbnailer();
            case "application/vnd.ms-excel":
                return new XLSThumbnailer();
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return new XLSXThumbnailer();
        }
        return null;
    }

    private enum ResolveContentType {
        image,
        text,
        unknown
    }
}

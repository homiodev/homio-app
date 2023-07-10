package org.homio.app.manager;

import static org.homio.app.model.entity.LocalBoardEntity.DEFAULT_DEVICE_ENTITY_ID;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.util.CommonUtils;
import org.homio.app.model.entity.LocalBoardEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ImageService {

    private final EntityContext entityContext;
    private final AddonService addonService;

    @SneakyThrows
    public ResponseEntity<InputStreamResource> getImage(String entityID) {
        Path filePath = CommonUtils.getImagePath().resolve(entityID);
        if (!Files.exists(filePath)) {
            LocalBoardEntity LocalBoardEntity = this.entityContext.getEntityRequire(DEFAULT_DEVICE_ENTITY_ID);
            filePath = Paths.get(LocalBoardEntity.getFileSystemRoot(), entityID);
        }
        if (Files.exists(filePath)) {
            return getImage(Files.newInputStream(filePath), MediaType.parseMediaType(Files.probeContentType(filePath)));
        }
        InputStream stream = addonService.getImageStream(entityID);
        if (stream != null) {
            return getImage(stream, MediaType.IMAGE_PNG);
        }
        return null;
    }

    @SneakyThrows
    public ResponseEntity<InputStreamResource> getImage(@NotNull InputStream stream, MediaType mediaType) {
        return CommonUtils.inputStreamToResource(stream, mediaType);
    }

    /*public boolean isExistsImage(String imageID) {
        ImageEntity imageEntity = imageRepository.getByEntityID(imageID);
        if (imageEntity != null) {
            if (imageEntity.toPath() != null) {
                Path imagePath = imageEntity.toPath();
                if (Files.exists(imagePath)) {
                    try {
                        String contentType = Files.probeContentType(imagePath);
                        return contentType != null;
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        return false;
    }

    public ImageEntity upload(String entityID, BufferedImage bufferedImage) {
        try {
            Path imagePath = CommonUtils.getImagePath().resolve(entityID);
            String ext = entityID.substring(entityID.length() - 3);
            ImageIO.write(bufferedImage, ext, imagePath.toFile());
            ImageEntity imageEntity = imageRepository.getByPath(imagePath.toAbsolutePath().toString());
            if (imageEntity == null) {
                imageEntity = new ImageEntity();
                imageEntity.setPath(imagePath.toAbsolutePath().toString());
            }
            imageEntity.setEntityID(entityID.substring(0, entityID.length() - ext.length() - 1));
            imageEntity.setOriginalWidth(bufferedImage.getWidth());
            imageEntity.setOriginalHeight(bufferedImage.getHeight());
            return entityContext.save(imageEntity);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServerException(e);
        }
    }*/
}

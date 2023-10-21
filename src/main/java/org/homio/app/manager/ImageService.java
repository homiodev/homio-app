package org.homio.app.manager;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.exception.NotFoundException;
import org.homio.api.util.CommonUtils;
import org.homio.app.model.entity.LocalBoardEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ImageService {

    private final Context context;
    private final AddonService addonService;

    @SneakyThrows
    public ImageResponse getImage(String entityID) {
        Path filePath = CommonUtils.getImagePath().resolve(entityID);
        if (!Files.exists(filePath)) {
            LocalBoardEntity localBoardEntity = this.context.db().getEntityRequire(LocalBoardEntity.class, PRIMARY_DEVICE);
            filePath = Paths.get(localBoardEntity.getFileSystemRoot(), entityID);
        }
        if (Files.exists(filePath)) {
            return new ImageResponse(
                    Files.newInputStream(filePath),
                    MediaType.parseMediaType(Files.probeContentType(filePath)));
        }
        InputStream stream = addonService.getImageStream(entityID);
        if (stream != null) {
            return new ImageResponse(stream, MediaType.IMAGE_PNG);
        }
        return null;
    }

    @SneakyThrows
    public ImageResponse getAddonImage(String addonID, String imageID) {
        AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID);
        InputStream stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("images/" + imageID);
        if (stream == null) {
            URL image = addonEntrypoint.getResource(imageID);
            if (image == null) {
                throw new NotFoundException("Unable to find image <" + imageID + "> of addon: " + addonID);
            }
            stream = image.openStream();
        }
        return new ImageResponse(stream, MediaType.IMAGE_PNG);

    }

    public record ImageResponse(InputStream stream, MediaType mediaType) {
    }
}

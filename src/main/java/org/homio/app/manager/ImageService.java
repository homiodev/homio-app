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
import org.apache.commons.lang3.StringUtils;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.util.CommonUtils;
import org.homio.app.model.entity.LocalBoardEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ImageService {

  private final Context context;
  private final AddonService addonService;

  @SneakyThrows
  public ImageResponse getImage(String entityID, String addonId, String imageIdentifier) {
    if(addonId != null) {
      return getAddonImage(addonId, StringUtils.defaultIfEmpty(imageIdentifier, entityID));
    }
    Path filePath = CommonUtils.getImagePath().resolve(entityID);
    if (!Files.exists(filePath)) {
      LocalBoardEntity localBoardEntity =
          this.context.db().getRequire(LocalBoardEntity.class, PRIMARY_DEVICE);
      filePath = Paths.get(localBoardEntity.getFileSystemRoot(), entityID);
    }
    if (Files.exists(filePath)) {
      return new ImageResponse(
          Files.newInputStream(filePath),
          MediaType.parseMediaType(Files.probeContentType(filePath)));
    }
    /*InputStream stream = addonService.getImageStream(entityID);
    if (stream != null) {
      return new ImageResponse(stream, MediaType.IMAGE_PNG);
    }*/
    if (imageIdentifier != null) {
      if (imageIdentifier.contains("###")) {
        return getImage(imageIdentifier.split("###")[0], null, null);
      }
    }
    /*if (addonId != null) {
      var response = getImage(addonId + "/" + entityID, null, null); // entityID is itemType
      if (response != null) {
        return response;
      }
      return getAddonImage(addonId, imageIdentifier);
    }*/
    return getAddonImage(entityID, imageIdentifier);
  }

  @SneakyThrows
  private ImageResponse getAddonImage(@NotNull String addonID, @Nullable String imageID) {
    AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID);
    URL resource = addonEntrypoint.getResource("images/" + imageID);
    if(resource == null) {
      resource = addonEntrypoint.getResource("images/" + imageID + ".png");
    }
    InputStream stream = resource == null ? addonService.getImageStream(addonID) : resource.openStream();
    return new ImageResponse(stream, MediaType.IMAGE_PNG);
  }

  public record ImageResponse(InputStream stream, MediaType mediaType) {}
}

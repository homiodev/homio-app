package org.touchhome.app.manager;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.app.utils.color.ColorThief;
import org.touchhome.app.utils.color.MMCQ;
import org.touchhome.app.utils.color.RGBUtil;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.common.exception.NotFoundException;

@Log4j2
@Component
@RequiredArgsConstructor
public class BundleService implements ContextRefreshed {

  // constructor parameters
  private final EntityContext entityContext;
  private Map<String, String> bundleColorMap;
  private Map<String, BundleEntrypoint> bundleMap;
  private Collection<BundleEntrypoint> bundleEntrypoints;

  @Override
  public void onContextRefresh() throws Exception {
    this.bundleEntrypoints = entityContext.getBeansOfType(BundleEntrypoint.class);
    this.bundleMap = bundleEntrypoints.stream().collect(Collectors.toMap(BundleEntrypoint::getBundleId, s -> s));
    this.bundleColorMap = new HashMap<>();

    for (BundleEntrypoint bundleEntrypoint : bundleEntrypoints) {
      try {
        URL imageURL = bundleEntrypoint.getBundleImageURL();
        BufferedImage img = ImageIO.read(Objects.requireNonNull(imageURL));
        MMCQ.CMap result = ColorThief.getColorMap(img, 5);
        MMCQ.VBox dominantColor = result.vboxes.get(bundleEntrypoint.getBundleImageColorIndex().ordinal());
        int[] rgb = dominantColor.avg(false);
        bundleColorMap.put(bundleEntrypoint.getBundleId(), RGBUtil.toRGBHexString(rgb));
      } catch (Exception ex) {
        log.error("Unable to start app due error in bundle: <{}>", bundleEntrypoint.getBundleId(), ex);
        throw ex;
      }
    }
  }

  public BundleEntrypoint getBundle(String bundleID) {
    BundleEntrypoint bundleEntrypoint = bundleMap.get(bundleID);
    if (bundleEntrypoint == null) {
      throw new NotFoundException("Unable to find bundle: " + bundleID);
    }
    return bundleEntrypoint;
  }

  public String getBundleColor(String bundleID) {
    return bundleColorMap.get(bundleID);
  }

  public Collection<BundleEntrypoint> getBundles() {
    return bundleEntrypoints;
  }
}

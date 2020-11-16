package org.touchhome.app.manager;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.utils.color.ColorThief;
import org.touchhome.app.utils.color.MMCQ;
import org.touchhome.app.utils.color.RGBUtil;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
@Component
public class BundleManager {
    private Map<String, String> bundleColorMap;
    private Map<String, BundleEntryPoint> bundleMap;
    private Collection<BundleEntryPoint> bundleEntryPoints;

    @SneakyThrows
    public void postConstruct(EntityContext entityContext) {
        this.bundleEntryPoints = entityContext.getBeansOfType(BundleEntryPoint.class);
        this.bundleMap = bundleEntryPoints.stream().collect(Collectors.toMap(BundleEntryPoint::getBundleId, s -> s));
        this.bundleColorMap = new HashMap<>();

        for (BundleEntryPoint bundleEntrypoint : bundleEntryPoints) {
            URL imageURL = bundleEntrypoint.getBundleImageURL();
            BufferedImage img = ImageIO.read(Objects.requireNonNull(imageURL));
            MMCQ.CMap result = ColorThief.getColorMap(img, 5);
            MMCQ.VBox dominantColor = result.vboxes.get(bundleEntrypoint.getBundleImageColorIndex().ordinal());
            int[] rgb = dominantColor.avg(false);
            bundleColorMap.put(bundleEntrypoint.getBundleId(), RGBUtil.toRGBHexString(rgb));
        }
    }

    public BundleEntryPoint getBundle(String bundleID) {
        BundleEntryPoint bundleEntrypoint = bundleMap.get(bundleID);
        if (bundleEntrypoint == null) {
            throw new NotFoundException("Unable to find bundle: " + bundleID);
        }
        return bundleEntrypoint;
    }

    public String getBundleColor(String bundleID) {
        return bundleColorMap.get(bundleID);
    }

    public Collection<BundleEntryPoint> getBundles() {
        return bundleEntryPoints;
    }
}

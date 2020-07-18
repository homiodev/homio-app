package org.touchhome.app.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.utils.color.ColorThief;
import org.touchhome.app.utils.color.MMCQ;
import org.touchhome.app.utils.color.RGBUtil;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.exception.NotFoundException;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class BundleManager {
    private final List<BundleEntrypoint> bundleEntrypoints;

    private final Map<String, String> bundleColorMap = new HashMap<>();
    private Map<String, BundleEntrypoint> bundleMap;

    @PostConstruct
    public void init() throws IOException {
        this.bundleMap = bundleEntrypoints.stream().collect(Collectors.toMap(BundleEntrypoint::getBundleId, s -> s));
        for (BundleEntrypoint bundleEntrypoint : bundleEntrypoints) {
            URL resource = bundleEntrypoint.getClass().getClassLoader().getResource(bundleEntrypoint.getBundleImage());
            BufferedImage img = ImageIO.read(Objects.requireNonNull(resource));
            MMCQ.CMap result = ColorThief.getColorMap(img, 5);
            MMCQ.VBox dominantColor = result.vboxes.get(bundleEntrypoint.getBundleImageColorIndex().ordinal());
            int[] rgb = dominantColor.avg(false);
            bundleColorMap.put(bundleEntrypoint.getBundleId(), RGBUtil.toRGBHexString(rgb));
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

    public List<BundleEntrypoint> getBundles() {
        return bundleEntrypoints;
    }
}

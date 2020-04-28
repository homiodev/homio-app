package org.touchhome.app.manager;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.utils.color.ColorThief;
import org.touchhome.app.utils.color.MMCQ;
import org.touchhome.app.utils.color.RGBUtil;
import org.touchhome.bundle.api.BundleContext;
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
@AllArgsConstructor
public class BundleManager {
    private final Map<String, String> bundleColorMap = new HashMap<>();
    private final List<BundleContext> bundleContexts;
    private Map<String, BundleContext> bundleMap;

    @PostConstruct
    public void init() throws IOException {
        this.bundleMap = bundleContexts.stream().collect(Collectors.toMap(BundleContext::getBundleId, s -> s));
        for (BundleContext bundleContext : bundleContexts) {
            URL resource = bundleContext.getClass().getClassLoader().getResource(bundleContext.getBundleImage());
            BufferedImage img = ImageIO.read(Objects.requireNonNull(resource));
            MMCQ.CMap result = ColorThief.getColorMap(img, 5);
            MMCQ.VBox dominantColor = result.vboxes.get(bundleContext.getBundleImageColorIndex().ordinal());
            int[] rgb = dominantColor.avg(false);
            bundleColorMap.put(bundleContext.getBundleId(), RGBUtil.toRGBHexString(rgb));
        }
    }

    public BundleContext getBundle(String bundleID) {
        BundleContext bundleContext = bundleMap.get(bundleID);
        if (bundleContext == null) {
            throw new NotFoundException("Unable to find bundle: " + bundleID);
        }
        return bundleContext;
    }

    public String getBundleColor(String bundleID) {
        return bundleColorMap.get(bundleID);
    }

    public List<BundleContext> getBundles() {
        return bundleContexts;
    }
}

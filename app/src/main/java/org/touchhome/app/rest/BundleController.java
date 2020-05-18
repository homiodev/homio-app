package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.BundleManager;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/rest/bundle")
@RequiredArgsConstructor
public class BundleController {

    private final BundleManager bundleManager;

    @GetMapping("{bundleID}/icon")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getBundleImage(@PathVariable("bundleID") String bundleID) {
        BundleContext bundleContext;
        String bundleImage;
        try {
            bundleContext = bundleManager.getBundle(bundleID);
            bundleImage = bundleContext.getBundleImage();
        } catch (Exception ex) {
            if (bundleID.contains("-")) {
                bundleContext = bundleManager.getBundle(bundleID.substring(0, bundleID.indexOf("-")));
                bundleImage = bundleID + ".png";
            } else {
                throw new IllegalArgumentException("Unable to find bundle with id: " + bundleID);
            }
        }
        InputStream imageStream = bundleContext.getClass().getClassLoader().getResourceAsStream(bundleImage);
        if (imageStream == null) {
            throw new NotFoundException("Unable to find bundle image: " + bundleImage + " of bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(imageStream, MediaType.IMAGE_PNG);
    }

    @GetMapping
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<BundleJson> getBundles() {
        List<BundleJson> bundles = new ArrayList<>(bundleManager.getBundles().size());
        for (BundleContext bundle : bundleManager.getBundles()) {
            bundles.add(new BundleJson(bundle.getBundleId(), bundleManager.getBundleColor(bundle.getBundleId()), bundle.order()));
        }
        Collections.sort(bundles);
        return bundles;
    }

    public BundleContext getBundle(String id) {
        return bundleManager.getBundles().stream().filter(s -> s.getBundleId().equals(id)).findAny().orElse(null);
    }

    @Getter
    @RequiredArgsConstructor
    private static class BundleJson implements Comparable<BundleJson> {
        private final String id;
        private final String color;
        private final int order;

        @Override
        public int compareTo(@NotNull BundleJson o) {
            return Integer.compare(order, o.order);
        }
    }
}

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
import org.touchhome.app.manager.BundleService;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/rest/bundle")
@RequiredArgsConstructor
public class BundleController {

    private final BundleService bundleService;
    private final List<Scratch3ExtensionBlocks> scratch3ExtensionBlocks;

    @GetMapping("/image/{bundleID}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getBundleImage(@PathVariable("bundleID") String bundleID) throws IOException {
        BundleEntryPoint bundleEntryPoint = bundleService.getBundle(bundleID.contains("-") ? bundleID.substring(0, bundleID.indexOf("-")) : bundleID);
        URL imageUrl = bundleEntryPoint.getResource(bundleID + ".png");
        if (imageUrl == null) {
            imageUrl = bundleEntryPoint.getBundleImageURL();
        }
        if (imageUrl == null) {
            throw new NotFoundException("Unable to find bundle image of bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(imageUrl.openStream(), MediaType.IMAGE_PNG);
    }

    @GetMapping("/image/{bundleID}/{baseEntityType:.+}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getBundleImage(@PathVariable("bundleID") String bundleID,
                                                              @PathVariable String baseEntityType) {
        BundleEntryPoint bundleEntrypoint = bundleService.getBundle(bundleID);
        InputStream stream = bundleEntrypoint.getClass().getClassLoader().getResourceAsStream("images/" + baseEntityType);
        if (stream == null) {
            throw new NotFoundException("Unable to find image <" + baseEntityType + "> of bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
    }

    public List<BundleJson> getBundles() {
        List<BundleJson> bundles = new ArrayList<>();
        for (BundleEntryPoint bundle : bundleService.getBundles()) {
            bundles.add(new BundleJson(bundle.getBundleId(), bundleService.getBundleColor(bundle.getBundleId()), bundle.order()));
        }
        Collections.sort(bundles);
        return bundles;
    }

    public BundleEntryPoint getBundle(String id) {
        return bundleService.getBundles().stream().filter(s -> s.getBundleId().equals(id)).findAny().orElse(null);
    }

    @Getter
    @RequiredArgsConstructor
    static class BundleJson implements Comparable<BundleJson> {
        private final String id;
        private final String color;
        private final int order;

        @Override
        public int compareTo(@NotNull BundleJson o) {
            return Integer.compare(order, o.order);
        }
    }
}

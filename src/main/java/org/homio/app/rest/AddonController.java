package org.homio.app.rest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.exception.NotFoundException;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.manager.AddonService;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/addon")
@RequiredArgsConstructor
public class AddonController {

    private final AddonService addonService;

    @GetMapping("/image/{addonID}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getAddonImage(@PathVariable("addonID") String addonID) throws IOException {
        AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID.contains("-") ? addonID.substring(0, addonID.indexOf("-")) : addonID);
        URL imageUrl = addonEntrypoint.getResource(addonID + ".png");
        if (imageUrl == null) {
            imageUrl = addonEntrypoint.getAddonImageURL();
        }
        if (imageUrl == null) {
            throw new NotFoundException("Unable to find addon image: " + addonID);
        }
        return CommonUtils.inputStreamToResource(imageUrl.openStream(), MediaType.IMAGE_PNG);
    }

    @GetMapping("/image/{addonID}/{baseEntityType:.+}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getAddonImage(
        @PathVariable("addonID") String addonID, @PathVariable String baseEntityType) {
        AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID);
        InputStream stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("images/" + baseEntityType);
        if (stream == null) {
            throw new NotFoundException("Unable to find image <" + baseEntityType + "> of addon: " + addonID);
        }
        return CommonUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
    }

    public List<AddonJson> getAddons() {
        List<AddonJson> addons = new ArrayList<>();
        for (AddonEntrypoint addonEntrypoint : addonService.getAddons()) {
            addons.add(new AddonJson(addonEntrypoint.getAddonID(),
                addonService.getAddonColor(addonEntrypoint.getAddonID()), addonEntrypoint.order()));
        }
        Collections.sort(addons);
        return addons;
    }

    @Getter
    @RequiredArgsConstructor
    static class AddonJson implements Comparable<AddonJson> {

        private final String id;
        private final String color;
        private final int order;

        @Override
        public int compareTo(@NotNull AddonController.AddonJson o) {
            return Integer.compare(order, o.order);
        }
    }
}

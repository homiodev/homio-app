package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.extloader.BundleService;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/rest/bundle/extra")
@RequiredArgsConstructor
public class ExtraBundleController {
    private final InternalManager internalManager;
    private final BundleService bundleService;

    @GetMapping
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<ExtraBundleJson> getInstalledBundles() {
        return internalManager.getBundles().values().stream()
                .map(b -> new ExtraBundleJson(b.getBundleContext().getBundleName(), b.getBundleContext().getVersion()))
                .collect(Collectors.toSet());
    }

    @PostMapping("{name}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void installBundle(@PathVariable("name") String name, @RequestBody InstallBundleRequest installBundleRequest) {
        bundleService.installBundle(name, installBundleRequest.getUrl(), installBundleRequest.getVersion());
    }

    @DeleteMapping("{name}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void uninstallBundle(@PathVariable("name") String name) {
        bundleService.uninstallBundle(name);
    }

    @Getter
    @RequiredArgsConstructor
    private static class ExtraBundleJson {
        private final String name;
        private final String version;
    }

    @Getter
    private static class InstallBundleRequest {
        private String url;
        private String version;
    }
}

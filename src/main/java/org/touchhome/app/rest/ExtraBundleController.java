package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
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
    private final BundleContextService bundleContextService;

    @GetMapping
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<ExtraBundleJson> getInstalledBundles() {
        return internalManager.getBundles().values().stream()
                .filter(b -> b.getBundleContext() != null)
                .map(b -> new ExtraBundleJson(b.getBundleContext()))
                .collect(Collectors.toSet());
    }

    @PostMapping("{bundleId}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void installBundle(@PathVariable("bundleId") String bundleId, @RequestBody InstallBundleRequest installBundleRequest) {
        bundleContextService.installBundle(bundleId, installBundleRequest.getUrl(), installBundleRequest.getVersion());
    }

    @DeleteMapping("{name}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void uninstallBundle(@PathVariable("name") String name) {
        bundleContextService.uninstallBundle(name);
    }

    @Getter
    @RequiredArgsConstructor
    private static class ExtraBundleJson {
        private final String name;
        private final String title;
        private final String version;
        private final String readme;

        public ExtraBundleJson(BundleContext bundleContext) {
            this.name = bundleContext.getBundleID();
            this.title = bundleContext.getPomFile().getName();
            this.version = bundleContext.getVersion();
            this.readme = bundleContext.getPomFile().getDescription();
        }
    }

    @Getter
    @Setter
    private static class InstallBundleRequest {
        private String url;
        private String version;
    }
}

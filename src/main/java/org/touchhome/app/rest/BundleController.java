package org.touchhome.app.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.touchhome.app.manager.BundleService;
import org.touchhome.app.setting.system.SystemAddonRepositoriesSetting;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.exception.NotFoundException;

@Log4j2
@RestController
@RequestMapping("/rest/bundle")
@RequiredArgsConstructor
public class BundleController {

    private final BundleService bundleService;
    private final RestTemplate restTemplate;
    private final EntityContext entityContext;

    private static final String URL_BUNDLES = "https://api.bintray.com/repos/touchhome/maven-repo-extra-bundles/packages?start_name=touchhome-bundle&attribute_values=1";
    private static final String URL_BUNDLE_VERSION = "https://api.bintray.com/packages/touchhome/maven-repo-extra-bundles/%s/versions/_latest";
    private static final String URL_BUNDLE = "https://api.bintray.com/packages/touchhome/maven-repo-extra-bundles/%s/versions/%s/files";
    private static final String URL_DOWNLOAD_FILE = "https://dl.bintray.com/touchhome/maven-repo-extra-bundles/%s";
    private static MavenXpp3Reader pomReader = new MavenXpp3Reader();


    @GetMapping
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Collection<PackageEntity> getBundleAddons() {
        Map<String, PackageEntity> bundleEntities = new HashMap<>();
        try {
            for (String repoURL : entityContext.setting().getValue(SystemAddonRepositoriesSetting.class)) {

            }

            /*log.info("Try fetch bundles");
            Bundle[] bundles = restTemplate.getForObject(URL_BUNDLES, Bundle[].class);
            if (bundles == null) {
                log.error("Unable to fetch bundles");
                return;
            }
            for (Bundle bundle : bundles) {
                String version = restTemplate.getForObject(String.format(URL_BUNDLE_VERSION, bundle.name), PackageEntity.class).getName();

                if (!this.bundleEntities.containsKey(bundle.name) || !this.bundleEntities.get(bundle.name).getVersion().equals(version)) {
                    try {
                        PackageEntity packageEntity = new PackageEntity();
                        bundle.files = restTemplate.getForObject(String.format(URL_BUNDLE, bundle.name, version), ContextFile[].class);
                        packageEntity.readme = restTemplate.getForObject(String.format(URL_DOWNLOAD_FILE, bundle.getContext("README.md").path),
                            String.class);
                        packageEntity.image = restTemplate.getForObject(String.format(URL_DOWNLOAD_FILE, bundle.getContext("image.png").path),
                            byte[].class);
                        restTemplate.getForObject(String.format(URL_DOWNLOAD_FILE, bundle.getContext(".pom").path), String.class);
                        packageEntity.pomFile = pomReader.read(
                            IOUtils.toInputStream(
                                restTemplate.getForObject(String.format(URL_DOWNLOAD_FILE, bundle.getContext(".pom").path), String.class)));
                        ContextFile jar = bundle.getContext("-jar-with-dependencies.jar");
                        packageEntity.jarUrl = String.format(URL_DOWNLOAD_FILE, jar.path);
                        packageEntity.name = bundle.name;
                        packageEntity.version = jar.version;
                        packageEntity.updated = jar.created;
                        packageEntity.size = jar.size;
                        this.bundleEntities.put(bundle.name, packageEntity);
                    } catch (Exception ex) {
                        log.warn("Unable to fetch bundle: <{}>. <{}>", bundle.name, ex.getMessage());
                    }
                }
            }
            log.info("Success fetched bundles");*/
        } catch (Exception ex) {
            throw new IllegalStateException("error.fetch_bundles", ex);
        }
        return bundleEntities.values();
    }

    @GetMapping("/image/{bundleID}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getBundleImage(@PathVariable("bundleID") String bundleID) throws IOException {
        BundleEntrypoint bundleEntrypoint = bundleService.getBundle(bundleID.contains("-") ? bundleID.substring(0, bundleID.indexOf("-")) : bundleID);
        URL imageUrl = bundleEntrypoint.getResource(bundleID + ".png");
        if (imageUrl == null) {
            imageUrl = bundleEntrypoint.getBundleImageURL();
        }
        if (imageUrl == null) {
            throw new NotFoundException("Unable to find bundle image of bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(imageUrl.openStream(), MediaType.IMAGE_PNG);
    }

    @GetMapping("/image/{bundleID}/{baseEntityType:.+}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ResponseEntity<InputStreamResource> getBundleImage(
        @PathVariable("bundleID") String bundleID, @PathVariable String baseEntityType) {
        BundleEntrypoint bundleEntrypoint = bundleService.getBundle(bundleID);
        InputStream stream = bundleEntrypoint.getClass().getClassLoader().getResourceAsStream("images/" + baseEntityType);
        if (stream == null) {
            throw new NotFoundException("Unable to find image <" + baseEntityType + "> of bundle: " + bundleID);
        }
        return TouchHomeUtils.inputStreamToResource(stream, MediaType.IMAGE_PNG);
    }

    public List<BundleJson> getBundles() {
        List<BundleJson> bundles = new ArrayList<>();
        for (BundleEntrypoint bundle : bundleService.getBundles()) {
            bundles.add(new BundleJson(bundle.getBundleId(), bundleService.getBundleColor(bundle.getBundleId()), bundle.order()));
        }
        Collections.sort(bundles);
        return bundles;
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

    @Getter
    private static class PackageEntity {

        @JsonIgnore
        public Model pomFile;
        private String name;
        private byte[] image;
        private String readme;
        private String jarUrl;
        private String version;
        private List<String> versions;
        private Date updated;
        private Integer size;

        public String getCategory() {
            return pomFile.getProperties().getProperty("category");
        }

        public String getWebsite() {
            return pomFile.getUrl();
        }

        public String getAuthor() {
            return pomFile.getDevelopers().stream().map(Contributor::getName).collect(Collectors.joining(", "));
        }

        public String getTitle() {
            return pomFile.getName();
        }
    }

    @Setter
    private static class Bundle {

        private String name;
        private ContextFile[] files;

        private ContextFile getContext(String name) {
            for (ContextFile contextFile : files) {
                if (contextFile.name.equals(name) || contextFile.name.endsWith(name)) {
                    return contextFile;
                }
            }
            throw new IllegalArgumentException("Unable to find context file: <" + name + "> in bundle: <" + this.name + ">");
        }
    }

    @Setter
    private static class ContextFile {

        private String name;
        private String path;
        private String version;
        private Date created;
        private Integer size;
    }
}

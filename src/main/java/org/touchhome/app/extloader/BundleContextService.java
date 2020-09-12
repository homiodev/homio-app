package org.touchhome.app.extloader;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.utils.Curl;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class BundleContextService {

    private final Environment env;
    private final EntityContextImpl entityContextImpl;
    private final ApplicationContext parentContext;
    private final Map<String, BundleContext> bundleContextMap = new HashMap<>();
    private final Map<String, BundleContext> artifactIdContextMap = new HashMap<>();

    public BundleContextService(Environment env, EntityContextImpl entityContextImpl, ApplicationContext parentContext) {
        this.env = env;
        this.entityContextImpl = entityContextImpl;
        this.parentContext = parentContext;
        for (String systemBundle : TouchHomeUtils.SYSTEM_BUNDLES) {
            BundleContext systemBundleContext = new BundleContext(systemBundle);
            this.bundleContextMap.put(systemBundle, systemBundleContext);
            this.artifactIdContextMap.put(systemBundle, systemBundleContext);
        }
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    @SneakyThrows
    public void loadBundlesFromPath() {
        Path bundlePath = TouchHomeUtils.getBundlePath();
        for (Path bundleContextFile : findBundleContextFilesFromPath(bundlePath)) {
            BundleContext bundleContext = new BundleContext(bundleContextFile);
            artifactIdContextMap.put(bundleContext.getPomFile().getArtifactId(), bundleContext);
        }
        for (BundleContext context : artifactIdContextMap.values()) {
            loadBundle(context);
        }
        entityContextImpl.addBundle(artifactIdContextMap);
    }

    private void loadBundlesFromPath(Path bundleContextFile) {
        BundleContext bundleContext = new BundleContext(bundleContextFile);
        artifactIdContextMap.put(bundleContext.getPomFile().getArtifactId(), bundleContext);
        loadBundle(bundleContext);
        entityContextImpl.addBundle(artifactIdContextMap);
    }

    private void loadBundle(String bundleId, Path bundleContextFile) {
        BundleContext context = null;
        String name = bundleId;
        if (bundleId == null || !bundleContextMap.containsKey(bundleId)) {
            context = new BundleContext(bundleContextFile);
            if (name == null) {
                name = context.getPomFile().getArtifactId();
            }
        }
        loadBundle(context);
    }

    private void loadBundle(BundleContext context) {
        if (!context.isLoaded() && !context.isInternal()) {
            log.info("Try load bundle context <{}>.", context.getPomFile().getArtifactId());
            try {
                if (loadContext(context)) {
                    log.info("bundle context <{}> registered successfully.", context.getPomFile().getArtifactId());
                } else {
                    log.info("bundle context <{}> already registered before.", context.getPomFile().getArtifactId());
                }
            } catch (Exception ex) {
                log.error("Unable to load bundle context <{}>.", context.getPomFile().getArtifactId(), ex);
            }
        }
        bundleContextMap.put(context.getBundleID(), context);
    }

    /**
     * Load bundle file (war/jar) into service. Creates separate spring context for this.
     */
    @SneakyThrows
    private boolean loadContext(BundleContext context) {
        if (context.isLoaded() || context.isInternal()) {
            return false;
        }
        for (String artifactId : context.getDependencies()) {
            this.loadContext(artifactIdContextMap.get(artifactId));
        }

        String jarFileName = context.getBundleContextFile().getFileName().toString();
        log.info("Adding jar <{}> to classpath", jarFileName);
        ClassLoader classLoader = new SingleBundleClassLoader(context.getBundleContextFile());

        // creates configuration builder to find all jar files
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new ResourcesScanner())
                .addClassLoader(classLoader);

        context.load(configurationBuilder, env, parentContext, classLoader);

        return true;
    }

    @SneakyThrows
    private void removeBundle(String bundleId) {
        BundleContext context = this.bundleContextMap.remove(bundleId);
        if (context != null) { // TODO: gggggggggggggggggg
            entityContextImpl.removeBundle(bundleId);
            context.destroy();
            Files.delete(context.getBundleContextFile());
            log.info("Bundle <{}> has been removed successfully", bundleId);
        } else {
            log.warn("Unable to find bundle <{}>", bundleId);
        }
    }

    private List<Path> findBundleContextFilesFromPath(Path basePath) throws IOException {
        return Files.list(basePath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }

    public void installBundle(String bundleId, String bundleUrl, String version) {
        BundleContext context = this.bundleContextMap.get(bundleId);
        if (context != null) {
            if (context.getVersion().equals(version)) {
                throw new IllegalStateException("Bundle <{}> already up to date");
            }
            removeBundle(bundleId);
        }
        Path path = TouchHomeUtils.getBundlePath().resolve(bundleId + ".jar");
        Curl.downloadToFile(bundleUrl, path);
        loadBundlesFromPath(path);
    }

    public void uninstallBundle(String name) {
        BundleContext context = this.bundleContextMap.get(name);
        if (context == null) {
            throw new IllegalStateException("Bundle <{}> not exists");
        }
        removeBundle(name);
    }
}

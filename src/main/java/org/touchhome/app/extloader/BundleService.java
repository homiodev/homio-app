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
import org.touchhome.app.manager.common.InternalManager;
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
public class BundleService {

    private final Environment env;
    private final InternalManager internalManager;
    private final ApplicationContext parentContext;
    private final BundleClassLoaderHolder bundleClassLoaderHolder;
    private final Map<String, BundleContext> bundleContextMap = new HashMap<>();

    public BundleService(Environment env, InternalManager internalManager, ApplicationContext parentContext, BundleClassLoaderHolder bundleClassLoaderHolder) {
        this.env = env;
        this.internalManager = internalManager;
        this.parentContext = parentContext;
        this.bundleClassLoaderHolder = bundleClassLoaderHolder;
        for (String systemBundle : TouchHomeUtils.SYSTEM_BUNDLES) {
            this.bundleContextMap.put(systemBundle, new BundleContext(systemBundle));
        }
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    @SneakyThrows
    public void loadBundlesFromPath() {
        Path bundlePath = TouchHomeUtils.getBundlePath();
        for (Path bundleContextFile : findBundleContextFilesFromPath(bundlePath)) {
            BundleContext context = new BundleContext(bundleContextFile);
            bundleContextMap.put(context.getPomFile().getArtifactId(), context);
        }
        for (String bundleName : bundleContextMap.keySet()) {
            loadBundle(bundleName, null);
        }
        internalManager.addBundle(bundleContextMap);
    }

    private void loadBundle(String bundleName, Path bundleContextFile) {
        if (bundleName == null) {
            BundleContext context = new BundleContext(bundleContextFile);
            bundleName = context.getPomFile().getArtifactId();
            if (!bundleContextMap.containsKey(bundleName)) {
                bundleContextMap.put(bundleName, context);
            }
        }
        if (!bundleContextMap.get(bundleName).isLoaded() && !bundleContextMap.get(bundleName).isInternal()) {
            log.info("Try load bundle context <{}>.", bundleName);
            try {
                if (loadContext(bundleName)) {
                    log.info("bundle context <{}> registered successfully.", bundleName);
                } else {
                    log.info("bundle context <{}> already registered before.", bundleName);
                }
            } catch (Exception ex) {
                log.error("Unable to load bundle context <{}>.", bundleName, ex);
            }
        }
    }

    /**
     * Load bundle file (war/jar) into service. Creates separate spring context for this.
     */
    @SneakyThrows
    private boolean loadContext(String bundleName) {
        BundleContext context = this.bundleContextMap.get(bundleName);
        if (context == null) {
            throw new IllegalArgumentException("Unable to find bundle context: " + bundleName);
        }
        if (context.isLoaded() || context.isInternal()) {
            return false;
        }
        for (String dependencyBundleName : context.getDependencies()) {
            this.loadContext(dependencyBundleName);
        }

        bundleClassLoaderHolder.addJar(bundleName, context.getBundleContextFile());

        // creates configuration builder to find all jar files
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new ResourcesScanner())
                .addClassLoader(bundleClassLoaderHolder.getBundleClassLoader(bundleName));

        context.load(configurationBuilder, env, parentContext);

        return true;
    }

    @SneakyThrows
    private void removeBundle(String bundleName) {
        BundleContext context = this.bundleContextMap.remove(bundleName);
        if (context != null) {
            internalManager.removeBundle(bundleName);
            context.destroy();
            Files.delete(context.getBundleContextFile());
            log.info("Bundle <{}> has been removed successfully", bundleName);
        } else {
            log.warn("Unable to find bundle <{}>", bundleName);
        }
    }

    private List<Path> findBundleContextFilesFromPath(Path basePath) throws IOException {
        return Files.list(basePath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }

    public void installBundle(String name, String bundleUrl, String version) {
        BundleContext context = this.bundleContextMap.get(name);
        if (context != null) {
            if (context.getVersion().equals(version)) {
                throw new IllegalStateException("Bundle <{}> already up to date");
            }
            removeBundle(name);
        }
        Path path = TouchHomeUtils.getBundlePath().resolve(name + ".jar");
        Curl.downloadToFile(bundleUrl, path);
        loadBundle(null, path);
        internalManager.addBundle(bundleContextMap);
    }

    public void uninstallBundle(String name) {
        BundleContext context = this.bundleContextMap.get(name);
        if (context == null) {
            throw new IllegalStateException("Bundle <{}> not exists");
        }
        removeBundle(name);
    }
}

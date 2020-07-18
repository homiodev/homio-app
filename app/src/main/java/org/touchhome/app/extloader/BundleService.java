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
    private final Map<String, BundleContext> bundleContextMap = new HashMap<>();

    public BundleService(Environment env, InternalManager internalManager, ApplicationContext parentContext) {
        this.env = env;
        this.internalManager = internalManager;
        this.parentContext = parentContext;
        for (String systemBundle : TouchHomeUtils.SYSTEM_BUNDLES) {
            this.bundleContextMap.put(systemBundle, new BundleContext(systemBundle));
        }
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    @SneakyThrows
    public void loadContextFromPath(Path contextFileHolder) {
        for (Path bundleContextFile : findBundleContextFilesFromPath(contextFileHolder)) {
            BundleContext context = new BundleContext(bundleContextFile);
            bundleContextMap.put(context.getPomFile().getArtifactId(), context);
        }
        for (String bundleName : bundleContextMap.keySet()) {
            if (!bundleContextMap.get(bundleName).isLoaded() && !bundleContextMap.get(bundleName).isInternal()) {
                log.info("Try load bundle context <{}> using path <{}>.", bundleName, contextFileHolder);
                try {
                    if (loadContext(bundleName)) {
                        log.info("bundle context <{}> registered successfully.", bundleName);
                    } else {
                        log.info("bundle context <{}> already registered before.", bundleName);
                    }
                } catch (Exception ex) {
                    log.error("Unable to load bundle context <{}> using path <{}>.", bundleName, contextFileHolder, ex);
                }
            }
        }
        internalManager.addBundle(bundleContextMap);
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
        BundleClassLoader bundleClassLoader = new BundleClassLoader(context.getBaseDir());

        // creates configuration builder to find all jar files
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new ResourcesScanner())
                .addClassLoader(bundleClassLoader);

        bundleClassLoader.register(context.getBundleContextFile().toString());
        context.load(configurationBuilder, env, bundleClassLoader, parentContext);

        return true;
    }

    private List<Path> findBundleContextFilesFromPath(Path staticPath) throws IOException {
        return Files.list(staticPath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }
}

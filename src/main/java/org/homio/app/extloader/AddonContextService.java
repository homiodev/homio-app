package org.homio.app.extloader;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.repeat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.homio.api.exception.ServerException;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Constants;
import org.homio.api.util.Lang;
import org.homio.app.HomioClassLoader;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.spring.ContextCreated;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class AddonContextService implements ContextCreated {

    private final Environment env;
    private final EntityContextImpl entityContext;
    private final ApplicationContext parentContext;
    private final Map<String, AddonContext> addonContextMap = new HashMap<>();
    private final Map<String, AddonContext> artifactIdContextMap = new HashMap<>();

    public AddonContextService(Environment env, EntityContextImpl entityContext, ApplicationContext parentContext) {
        this.env = env;
        this.entityContext = entityContext;
        this.parentContext = parentContext;
        for (String systemAddon : Constants.SYSTEM_ADDONS) {
            AddonContext systemAddonContext = new AddonContext(systemAddon);
            this.addonContextMap.put(systemAddon, systemAddonContext);
            this.artifactIdContextMap.put(systemAddon, systemAddonContext);
        }
        entityContext.bgp().runDirectoryWatchdog(CommonUtils.getAddonPath(), watchEvent -> {
            String eventType = watchEvent.kind().name();
            Path path = CommonUtils.getAddonPath().resolve(watchEvent.context());
            log.info("Detect addon directory changes: {}/{}", watchEvent.kind(), path);
            switch (eventType) {
                case "ENTRY_CREATE":
                    loadAddonFromPath(path);
                    break;
                case "ENTRY_DELETE":
                    removeAddonFromPath(path);
                    break;
                case "ENTRY_MODIFY":
                    reloadAddonFromPath(path);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + watchEvent.kind());
            }
        });
    }

    @SneakyThrows
    public void installAddon(String addonID, String addonUrl, String version) {
        AddonContext context = this.addonContextMap.get(addonID);
        if (context != null) {
            if (context.getVersion().equals(version)) {
                throw new ServerException("Addon <{}> already up to date");
            }
            removeAddon(addonID);
        }
        Path path = CommonUtils.getAddonPath().resolve(addonID + ".jar");
        FileUtils.copyURLToFile(new URL(format(addonUrl, version)), path.toFile(), 30000, 30000);
        loadAddonFromPath(path);
    }

    public void uninstallAddon(String name) {
        AddonContext context = this.addonContextMap.get(name);
        if (context == null) {
            throw new ServerException(Lang.getServerMessage("ADDON_NOT_EXISTS", name));
        }
        removeAddon(name);
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        Path addonPath = CommonUtils.getAddonPath();
        for (Path contextFile : findAddonContextFilesFromPath(addonPath)) {
            try {
                AddonContext addonContext = new AddonContext(contextFile);
                artifactIdContextMap.put(addonContext.getPomFile().getArtifactId(), addonContext);
            } catch (Exception ex) {
                log.error("\n#{}\nUnable to parse addon: {}\n#{}",
                    repeat("-", 50), contextFile.getFileName(), repeat("-", 50));
            }
        }
        for (AddonContext context : artifactIdContextMap.values()) {
            loadAddon(context);
        }
        this.entityContext.getEntityContextAddon().addAddons(artifactIdContextMap);
    }

    private void loadAddonFromPath(Path addonContextFile) {
        AddonContext addonContext = new AddonContext(addonContextFile);
        addAddonFromPath(addonContext);
    }

    private void addAddonFromPath(AddonContext addonContext) {
        artifactIdContextMap.put(addonContext.getPomFile().getArtifactId(), addonContext);
        loadAddon(addonContext);
        entityContext.getEntityContextAddon().addAddons(artifactIdContextMap);
    }

    private void loadAddon(AddonContext context) {
        if (!context.isLoaded() && !context.isInternal()) {
            log.info("Try load addon context <{}>.", context.getPomFile().getArtifactId());
            try {
                if (loadContext(context)) {
                    log.info("Addon context <{}> registered successfully.", context.getPomFile().getArtifactId());
                    addonContextMap.put(context.getAddonID(), context);
                } else {
                    log.info("Addon context <{}> already registered before.", context.getPomFile().getArtifactId());
                }
            } catch (Exception ex) {
                addonContextMap.remove(context.getAddonID());
                log.error("Unable to load addon context <{}>.", context.getPomFile().getArtifactId(), ex);
            }
        }
    }

    /**
     * Load addon file (war/jar) into service. Creates separate spring context for this.
     */
    @SneakyThrows
    private boolean loadContext(AddonContext context) {
        if (context.isLoaded() || context.isInternal()) {
            return false;
        }
        for (String artifactId : context.getDependencies()) {
            this.loadContext(artifactIdContextMap.get(artifactId));
        }

        String jarFileName = context.getContextFile().getFileName().toString();
        log.info("Adding jar <{}> to classpath", jarFileName);
        ClassLoader classLoader = new AddonClassLoader(context.getContextFile());

        // creates configuration builder to find all jar files
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
            .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new ResourcesScanner())
            .addClassLoader(classLoader);

        context.load(configurationBuilder, env, parentContext, classLoader);

        return true;
    }

    @SneakyThrows
    private void removeAddon(String addonID) {
        AddonContext context = this.addonContextMap.remove(addonID);
        if (context != null) {
            entityContext.getEntityContextAddon().removeAddon(addonID);
            HomioClassLoader.removeClassLoader(context.getAddonID());
            context.getConfig().destroy();
            Files.delete(context.getContextFile());
            log.info("Addon <{}> has been removed successfully", addonID);
        } else {
            log.warn("Unable to find addon <{}>", addonID);
        }
    }

    private List<Path> findAddonContextFilesFromPath(Path basePath) throws IOException {
        return Files.list(basePath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }

    private void reloadAddonFromPath(Path path) {
        try {
            AddonContext addonContext = new AddonContext(path);
            AddonContext existedAddonContext = artifactIdContextMap.get(addonContext.getAddonID());
            if (addonContext.equals(existedAddonContext)) {
                removeAddon(addonContext.getAddonID());
                addAddonFromPath(addonContext);
            }
        } catch (Exception ex) {
            log.error("Unable to reload addon: {}", path.getFileName());
        }
    }

    private void removeAddonFromPath(Path path) {
        try {
            AddonContext addonContext = new AddonContext(path);
            if (artifactIdContextMap.containsKey(addonContext.getAddonID())) {
                removeAddon(addonContext.getAddonID());
                loadAddonFromPath(path);
            }
        } catch (Exception ex) {
            log.error("Unable to remove addon: {}", path.getFileName());
        }
    }
}

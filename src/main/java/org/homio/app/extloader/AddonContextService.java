package org.homio.app.extloader;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.repeat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.exception.ServerException;
import org.homio.api.ui.field.ProgressBar;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Constants;
import org.homio.api.util.Curl;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.spring.ContextCreated;
import org.jetbrains.annotations.NotNull;
import org.reflections.scanners.Scanners;
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
            Path path = CommonUtils.getAddonPath().resolve(watchEvent.context());
            log.info("Detect addon directory changes: {}/{}", watchEvent.kind(), path);
            loadAddonFromPath(path);
        }, StandardWatchEventKinds.ENTRY_CREATE);
    }

    @SneakyThrows
    public void installAddon(String addonID, String addonUrl, String version, ProgressBar progressBar) {
        AddonContext context = this.addonContextMap.get(addonID);
        if (context != null && context.getVersion().equals(version)) {
            throw new ServerException(Lang.getServerMessage("ERROR.ADDON_INSTALLED", addonID));
        }
        // copy addon jar before delete it from system
        Path downloadPath = CommonUtils.getAddonPath().resolve(addonID + ".jar_original");
        Curl.downloadWithProgress(format(addonUrl, version), downloadPath, progressBar);
        progressBar.progress(50, "Installing addon...");
        if (context != null) {
            removeAddon(context);
        }
        Path addonPath = CommonUtils.getAddonPath().resolve(addonID + ".jar");
        Thread.sleep(500); // wait to make sure downloadPath closed all handlers
        Files.move(downloadPath, addonPath);

        try {
            loadAddonFromPath(addonPath);
        } catch (Exception ex) {
            deleteAddonFileWithRetry(addonPath);
            throw ex;
        }
    }

    public void uninstallAddon(String name) {
        AddonContext context = this.addonContextMap.get(name);
        if (context == null || context.isInternal() || !context.isLoaded()) {
            throw new ServerException(Lang.getServerMessage("ADDON_NOT_EXISTS", name));
        }
        removeAddon(context);
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        entityContext.ui().addNotificationBlockOptional("addons", "Addons", "fas fa-file-zipper", "#FF4400");
        Path addonPath = CommonUtils.getAddonPath();
        for (Path contextFile : findAddonContextFilesFromPath(addonPath)) {
            try {
                AddonContext addonContext = new AddonContext(contextFile);
                if (!AddonContext.validVersion(addonContext.getVersion(), entityContext.setting().getApplicationMajorVersion())) {
                    log.error("Unable to launch addon {}. Incompatible version", addonContext.getVersion());
                } else {
                    artifactIdContextMap.put(addonContext.getPomFile().getArtifactId(), addonContext);
                }
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

    private void loadAddonFromPath(Path addonContextFile) throws Exception {
        addAddonFromPath(new AddonContext(addonContextFile));
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
                throw ex;
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
            .setScanners(Scanners.SubTypes.filterResultsBy(s -> true), Scanners.TypesAnnotated, Scanners.Resources)
            .setClassLoaders(new ClassLoader[]{classLoader});

        context.load(configurationBuilder, env, parentContext, classLoader);

        return true;
    }

    @SneakyThrows
    private void removeAddon(@NotNull AddonContext context) {
        String addonID = context.getAddonID();
        log.warn("Remove addon: {}", addonID);
        entityContext.getEntityContextAddon().removeAddon(addonID);

        deleteAddonFileWithRetry(context.getContextFile());
        if (Files.exists(context.getContextFile())) {
            log.error("Addon <{}> has been stopped but unable to delete file. File will be removed on restart", addonID);
            entityContext.bgp().executeOnExit(() -> Files.deleteIfExists(context.getContextFile()));
        }
        log.info("Addon <{}> has been removed successfully", addonID);
    }

    private List<Path> findAddonContextFilesFromPath(Path basePath) throws IOException {
        return Files.list(basePath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }

    private void deleteAddonFileWithRetry(Path path) throws InterruptedException {
        int i = 10;
        while (i-- > 0) {
            try {
                Files.deleteIfExists(path);
                break;
            } catch (Exception ignore) {}
            Thread.sleep(500);
        }
    }
}

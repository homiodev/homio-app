package org.homio.app.manager.common.impl;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.repeat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.ContextUI.NotificationBlockBuilder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.repository.GitHubProject;
import org.homio.api.setting.SettingPluginPackageInstall.PackageContext;
import org.homio.api.setting.SettingPluginPackageInstall.PackageModel;
import org.homio.api.setting.SettingPluginPackageInstall.PackageRequest;
import org.homio.api.ui.UI.Color;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Constants;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.app.HomioClassLoader;
import org.homio.app.config.ExtRequestMappingHandlerMapping;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.extloader.AddonClassLoader;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.setting.system.SystemAddonLibraryManagerSetting;
import org.homio.app.utils.HardwareUtils;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;

@Log4j2
public class ContextAddonImpl {

    // count how much add/remove addon invokes
    public static int ADDON_UPDATE_COUNT = 0;
    private final Map<String, AddonContext> addonContextMap = new ConcurrentHashMap<>();
    private final ContextImpl context;
    private final CacheService cacheService;
    @Setter
    private ApplicationContext applicationContext;

    public ContextAddonImpl(ContextImpl context, CacheService cacheService) {
        this.context = context;
        this.cacheService = cacheService;

        for (String systemAddon : Constants.SYSTEM_ADDONS) {
            AddonContext systemAddonContext = new AddonContext(systemAddon);
            this.addonContextMap.put(systemAddon, systemAddonContext);
        }
    }

    public void initializeInlineAddons() {
        log.info("Initialize addons...");
        ArrayList<AddonEntrypoint> addonEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(AddonEntrypoint.class).values());
        Collections.sort(addonEntrypoints);
        log.info("Found addons: \r\n{}",
            addonEntrypoints.stream().map(AddonEntrypoint::getAddonID)
                            .collect(Collectors.joining("\r\n")));
        for (AddonEntrypoint entrypoint : addonEntrypoints) {
            //this.addons.put("addon-" + entrypoint.getAddonID(), new InternalAddonContext(entrypoint, null));
            fireAddonEntrypoint(entrypoint);
        }
        log.info("Done initialize addons");
    }

    public List<AddonContext> getInstalledAddons() {
        return addonContextMap.values().stream().filter(AddonContext::isInitialized).collect(Collectors.toList());
    }

    public void disableAddon(String addonShortName) {
        try {
            uninstallAddon("addon-" + addonShortName, false);
        } catch (Exception ex) {
            log.error("Error during disable addon: 'addon-{}'. Msg: {}", addonShortName, ex.getMessage());
        }
    }

    private void fireAddonEntrypoint(AddonEntrypoint entrypoint) {
        try {
            entrypoint.init();
            entrypoint.onContextRefresh();
        } catch (Exception ex) {
            log.error("Unable to call addon init(...): {}", entrypoint.getAddonID(), ex);
            throw ex;
        }
    }

    // initialize all installed addons as bunch
    public void initializeAddons(Map<String, AddonContext> artifactIdContextMap) {
        List<AddonContext> loadedAddons = artifactIdContextMap.values().stream().filter(context ->
                !context.isInternal() && !context.isInitialized() && context.isLoaded()).toList();
        if (loadedAddons.isEmpty()) {
            return;
        }
        cacheService.clearCache();

        for (AddonContext addonContext : loadedAddons) {
            addonContext.setInitialized(true);
            context.setting().addSettingsFromClassLoader(addonContext);
            applicationContext.getBean(ExtRequestMappingHandlerMapping.class).registerAddonRestControllers(addonContext);
        }
        context.rebuildRepositoryByPrefixMap();
        applicationContext.getBean(TransactionManagerContext.class).invalidate();
        context.fireRefreshBeans();
        // we need rebuild Hibernate entityManagerFactory to fetch new models
        cacheService.clearCache();
        context.registerAllFieldSubTypes();
        Lang.clear();

        for (AddonContext addonContext : loadedAddons) {
            ApplicationContext appContext = addonContext.getApplicationContext();
            for (AddonEntrypoint addonEntrypoint : appContext.getBeansOfType(AddonEntrypoint.class).values()) {
                fireAddonEntrypoint(addonEntrypoint);
            }
        }

        ADDON_UPDATE_COUNT++;
    }

    /**
     * Load context from specific file 'contextFile' and wraps logging info
     */
    public void onContextCreated() throws Exception {
        context.ui().notification().addBlock("addons", "Addons", new Icon("fas fa-file-zipper", "#FF4400"),
                block -> block.setBorderColor("#FF4400"));
        Path addonPath = CommonUtils.getAddonPath();
        int appVersion = context.setting().getApplicationMajorVersion();
        for (Path contextFile : findAddonContextFilesFromPath(addonPath)) {
            try {
                AddonContext addonContext = new AddonContext(contextFile);
                // 0 mean develop mode
                if (appVersion != 0 && !AddonContext.validVersion(addonContext.getVersion(), appVersion)) {
                    log.error("Unable to launch addon {}. Incompatible version", addonContext.getVersion());
                } else {
                    addonContextMap.put(addonContext.getPomFile().getArtifactId(), addonContext);
                }
            } catch (Exception ex) {
                log.error("\n#{}\nUnable to parse addon: {}\n#{}",
                        repeat("-", 50), contextFile.getFileName(), repeat("-", 50));
            }
        }
        // set up addons, create spring contexts,...
        for (AddonContext context : addonContextMap.values()) {
            setupAddonContext(context);
        }
        this.context.getAddon().initializeAddons(addonContextMap);
    }

    @SneakyThrows
    public synchronized void installAddon(String addonID, String addonUrl, String version, ProgressBar progressBar) {
        AddonContext context = this.addonContextMap.get(addonID);
        if (context != null && context.getVersion().equals(version)) {
            throw new ServerException("ERROR.ADDON_INSTALLED", addonID);
        }
        // download addon jar before delete it from system to make sure that
        Path addonPath;
        if (context != null) { // update/downgrade addon
            Path downloadPath = CommonUtils.getAddonPath().resolve(addonID + ".jar_original");
            Curl.downloadWithProgress(format(addonUrl, version), downloadPath, progressBar);
            removeAddon(context, true);
            addonPath = CommonUtils.getAddonPath().resolve(addonID + ".jar");
            Thread.sleep(500); // wait to make sure downloadPath close all connections
            Files.move(downloadPath, addonPath);
        } else { // install addon
            addonPath = CommonUtils.getAddonPath().resolve(addonID + ".jar");
            Curl.downloadWithProgress(format(addonUrl, version), addonPath, progressBar);
        }

        progressBar.progress(50, "Installing addon...");
        try {
            addAddonFromPath(new AddonContext(addonPath));
        } catch (Exception ex) {
            deleteFileWithRetry(addonPath);
            throw ex;
        }
    }

    public synchronized void uninstallAddon(String name, boolean deleteFile) {
        AddonContext context = this.addonContextMap.get(name);
        if (context == null || context.isInternal() || !context.isLoaded()) {
            throw new ServerException("ADDON_NOT_EXISTS", name);
        }
        removeAddon(context, deleteFile);
    }

    private void destroyAddonContext(@NotNull AddonContext addonContext, boolean deleteFile) {
        AnnotationConfigApplicationContext appContext = addonContext.getApplicationContext();
        log.warn("Destroy {} entrypoint", addonContext.getAddonID());
        appContext.getBean(AddonEntrypoint.class).destroy();
        addonContext.fireCloseListeners();
        context.getAllApplicationContexts().remove(appContext);
        appContext.close(); // destroy spring context
        HomioClassLoader.removeClassLoader(addonContext.getAddonID());
        applicationContext.getBean(TransactionManagerContext.class).invalidate();
        Lang.clear();

        context.fireRefreshBeans();
        context.rebuildRepositoryByPrefixMap();

        if (deleteFile) {
            context.ui().notification().updateBlock("addons", builder -> builder.removeInfo(addonContext.getAddonID()));
        } else {
            context.ui().notification().updateBlock("addons", builder -> {
                builder.removeInfo(addonContext.getAddonID());
                addAddonNotificationRow(addonContext, builder, true);
            });
        }
        addonContextMap.remove(addonContext.getAddonID());

        ADDON_UPDATE_COUNT++;
    }

    private void addAddonFromPath(AddonContext addonContext) {
        addonContextMap.put(addonContext.getPomFile().getArtifactId(), addonContext);
        setupAddonContext(addonContext);
        context.getAddon().initializeAddons(addonContextMap);
    }

    /**
     * Fulfill addon context from pom file, create spring context, add jar class loader
     */
    private void setupAddonContext(AddonContext addonContext) {
        if (!addonContext.isLoaded() && !addonContext.isInternal()) {
            log.info("Try load addon context <{}>.", addonContext.getPomFile().getArtifactId());
            try {
                if (loadContext(addonContext)) {
                    context.getAllApplicationContexts().add(addonContext.getApplicationContext());

                    // currently all copied resources will be kept on file system
                    URL externalFiles = addonContext.getClassLoader().getResource("external_files.7z");
                    // copy resource only if size not match or if not exists
                    HardwareUtils.copyResources(externalFiles);

                    context.ui().notification().updateBlock("addons",
                        builder -> addAddonNotificationRow(addonContext, builder, false));

                    log.info("Addon context <{}> registered successfully.", addonContext.getPomFile().getArtifactId());
                    addonContextMap.put(addonContext.getAddonID(), addonContext);
                } else {
                    log.info("Addon context <{}> already registered before.", addonContext.getPomFile().getArtifactId());
                }
            } catch (Exception ex) {
                context.ui().notification().updateBlock("addons",
                    builder -> addAddonNotificationRow(addonContext, builder, true));
                addonContextMap.remove(addonContext.getAddonID());
                log.error("Unable to load addon context <{}>.", addonContext.getPomFile().getArtifactId(), ex);
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
        /*for (String artifactId : context.getDependencies()) {
            this.loadContext(addonContextMap.get(artifactId));
        }*/

        String jarFileName = context.getContextFile().getFileName().toString();
        log.info("Adding jar <{}> to classpath", jarFileName);
        ClassLoader classLoader = new AddonClassLoader(context.getContextFile());

        // creates configuration builder to find all jar files
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setScanners(Scanners.SubTypes.filterResultsBy(s -> true), Scanners.TypesAnnotated, Scanners.Resources)
                .setClassLoaders(new ClassLoader[]{classLoader});

        context.load(configurationBuilder, applicationContext.getBean(Environment.class), applicationContext, classLoader);

        return true;
    }

    @SneakyThrows
    private void removeAddon(@NotNull AddonContext addonContext, boolean deleteFile) {
        String addonID = addonContext.getAddonID();
        log.warn("Remove addon: {}", addonID);
        destroyAddonContext(addonContext, deleteFile);

        if (deleteFile) {
            deleteFileWithRetry(addonContext.getContextFile());
            if (Files.exists(addonContext.getContextFile())) {
                log.error("Addon <{}> has been stopped but unable to delete file. File will be removed on restart", addonID);
                context.bgp().executeOnExit("Delete addon " + addonContext.getAddonID(),
                    () -> Files.deleteIfExists(addonContext.getContextFile()));
            }
            log.info("Addon <{}> has been removed successfully", addonID);
        } else {
            log.info("Addon <{}> has been disabled successfully", addonID);
        }
    }

    private List<Path> findAddonContextFilesFromPath(Path basePath) throws IOException {
        return Files.list(basePath).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
    }

    private void deleteFileWithRetry(Path path) throws InterruptedException {
        int i = 10;
        while (i-- > 0) {
            try {
                Files.deleteIfExists(path);
                break;
            } catch (Exception ignore) {
            }
            Thread.sleep(500);
        }
    }

    private void addAddonNotificationRow(@NotNull AddonContext addonContext, @NotNull NotificationBlockBuilder builder, boolean disabledAddon) {
        String key = addonContext.getAddonID();
        Icon icon = new Icon(addonContext.isLoaded() ? "fas fa-puzzle-piece" : "fas fa-bug", addonContext.isLoaded() ? Color.GREEN : Color.RED);
        String info = addonContext.getAddonFriendlyName() + (disabledAddon ? "(Disabled)" : "");
        PackageModel packageModel = getPackageModel(key);
        List<OptionModel> versions = packageModel == null ? List.of() :
            GitHubProject.getReleasesSince(addonContext.getVersion(), OptionModel.list(packageModel.getVersions()), false);

        if (versions.isEmpty()) {
            builder.addInfo(key, icon, info).setRightText(addonContext.getVersion());
        } else {
            builder.addFlexAction(key, flex -> {
                flex.addInfo(info).setIcon(icon);
                flex.addSelectBox("versions", (context, params) ->
                        handleUpdateAddon(addonContext, key, context, params, versions, packageModel))
                        .setHighlightSelected(false)
                    .setOptions(versions)
                        .setAsButton(new Icon("fas fa-cloud-download-alt", Color.PRIMARY_COLOR), addonContext.getVersion())
                        .setHeight(20).setPrimary(true);
            });
        }
    }

    @Nullable
    private ActionResponseModel handleUpdateAddon(@NotNull AddonContext addonContext,
        @NotNull String key,
        @NotNull Context context,
        @NotNull JSONObject params,
        @NotNull List<OptionModel> versions,
        @NotNull PackageModel packageModel) {
        String newVersion = params.getString("value");
        if (OptionModel.getByKey(versions, newVersion) != null) {
            String question = Lang.getServerMessage("PACKAGE_UPDATE_QUESTION",
                    FlowMap.of("NAME", addonContext.getPomFile().getName(), "VERSION", newVersion));
            context.ui().dialog().sendConfirmation("update-" + key, "DIALOG.TITLE.UPDATE_PACKAGE", () ->
                context.getBean(AddonService.class).installPackage(
                            new SystemAddonLibraryManagerSetting(),
                            new PackageRequest().setName(packageModel.getName())
                                    .setVersion(newVersion)
                                    .setUrl(packageModel.getJarUrl())), Collections.singletonList(question), null);
            return null;
        }
        return ActionResponseModel.showError("Unable to find package or version");
    }

    @SneakyThrows
    private @Nullable PackageModel getPackageModel(String addonID) {
        var addonManager = new SystemAddonLibraryManagerSetting();
        PackageContext allPackages = addonManager.allPackages(context);
        PackageModel packageModel = allPackages.getPackages().stream().filter(p -> p.getName().equals(addonID)).findAny().orElse(null);
        if (packageModel == null) {
            log.error("Unable to find addon '{}' in repositories", addonID);
        } else {
            return packageModel;
        }
        return null;
    }
}

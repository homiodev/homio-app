package org.homio.app.manager.common;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.extloader.BundleClassLoaderHolder;
import org.homio.app.extloader.BundleContext;
import org.homio.app.manager.CacheService;
import org.homio.app.setting.system.SystemBundleLibraryManagerSetting;
import org.homio.app.utils.HardwareUtils;
import org.homio.bundle.api.BundleEntrypoint;
import org.homio.bundle.api.exception.NotFoundException;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.repository.GitHubProject;
import org.homio.bundle.api.setting.SettingPluginPackageInstall.PackageContext;
import org.homio.bundle.api.setting.SettingPluginPackageInstall.PackageModel;
import org.homio.bundle.api.ui.UI.Color;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.widget.WidgetBaseTemplate;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationContext;

@Log4j2
@RequiredArgsConstructor
public class EntityContextBundleImpl {

    @Getter
    private final Map<String, InternalBundleContext> bundles = new LinkedHashMap<>();
    private final EntityContextImpl entityContext;
    private final CacheService cacheService;
    // count how much addBundle/removeBundle invokes
    public static int BUNDLE_UPDATE_COUNT = 0;
    private UIInputBuilder addonRowsBuilder;

    public void initialiseInlineBundles(ApplicationContext applicationContext) {
        log.info("Initialize bundles...");
        ArrayList<BundleEntrypoint> bundleEntrypoint = new ArrayList<>(applicationContext.getBeansOfType(BundleEntrypoint.class).values());
        Collections.sort(bundleEntrypoint);
        for (BundleEntrypoint entrypoint : bundleEntrypoint) {
            this.bundles.put(entrypoint.getBundleId(), new InternalBundleContext(entrypoint, null));
            log.info("Init bundle: <{}>", entrypoint.getBundleId());
            try {
                entrypoint.init();
                entrypoint.onContextRefresh();
            } catch (Exception ex) {
                log.fatal("Unable to init bundle: " + entrypoint.getBundleId(), ex);
                throw ex;
            }
        }
        log.info("Done initialize bundles");
    }

    public void addBundle(Map<String, BundleContext> artifactIdContextMap) {
        for (String artifactId : artifactIdContextMap.keySet()) {
            this.addBundle(artifactIdContextMap.get(artifactId), artifactIdContextMap);
        }
        BUNDLE_UPDATE_COUNT++;
    }

    public void removeBundle(String bundleId) {
        InternalBundleContext internalBundleContext = bundles.remove(bundleId);
        if (internalBundleContext != null) {
            this.removeBundle(internalBundleContext.bundleContext);
        }
    }

    public Object getBeanOfBundleBySimpleName(String bundle, String className) {
        InternalBundleContext internalBundleContext = this.bundles.get(bundle);
        if (internalBundleContext == null) {
            throw new NotFoundException("Unable to find bundle <" + bundle + ">");
        }
        Object o = internalBundleContext.fieldTypes.get(className);
        if (o == null) {
            throw new NotFoundException("Unable to find class <" + className + "> in bundle <" + bundle + ">");
        }
        return o;
    }

    private void removeBundle(BundleContext bundleContext) {
        if (!bundleContext.isInternal() && bundleContext.isInstalled()) {
            ApplicationContext context = bundleContext.getApplicationContext();
            context.getBean(BundleEntrypoint.class).destroy();
            entityContext.getAllApplicationContexts().remove(context);

            this.cacheService.clearCache();

            entityContext.rebuildAllRepositories(bundleContext.getApplicationContext(), false);
            entityContext.updateBeans(bundleContext, bundleContext.getApplicationContext(), false);
            BUNDLE_UPDATE_COUNT++;
        }
    }

    private void addBundle(BundleContext bundleContext, Map<String, BundleContext> artifactIdToContextMap) {
        if (!bundleContext.isInternal() && !bundleContext.isInstalled()) {
            if (!entityContext.ui().isHasNotificationBlock("addons")) {
                entityContext.ui().addNotificationBlock("addons", "Addons", "fas fa-file-zipper", "#FF4400", builder -> {
                    builder.blockActionBuilder(addonRowsBuilder -> {
                        this.addonRowsBuilder = addonRowsBuilder;
                    });
                });
            }
            String icon = bundleContext.isLoaded() ? "fas fa-puzzle-piece" : "fas fa-bug";
            String color = bundleContext.isLoaded() ? Color.GREEN : Color.RED;
            String info = bundleContext.getBundleFriendlyName();
            List<String> versions = getAddonReleasesSince(bundleContext.getPomFile().getArtifactId(), bundleContext.getVersion());
            entityContext.ui().updateNotificationBlock("addons",
                builder -> {
                    if (versions == null || versions.isEmpty()) {
                        builder.addInfo("", info, null, icon, color, bundleContext.getVersion(), null);
                    } else {
                        builder.addFlexAction(bundleContext.getBundleID(), flex -> {
                            flex.addInfo(info).setIcon(icon, color);
                            flex.addSelectBox("versions", (entityContext, params) -> {
                                System.out.println("BEST");
                                return ActionResponseModel.success();
                            }).setOptions(OptionModel.list(versions)).setAsButton(null, null, bundleContext.getVersion());
                        });
                    }
                });

            if (!bundleContext.isLoaded()) {
                return;
            }

            entityContext.getAllApplicationContexts().add(bundleContext.getApplicationContext());
            bundleContext.setInstalled(true);
            for (String bundleDependency : bundleContext.getDependencies()) {
                addBundle(artifactIdToContextMap.get(bundleDependency), artifactIdToContextMap);
            }
            ApplicationContext context = bundleContext.getApplicationContext();

            this.cacheService.clearCache();

            URL externalFiles = entityContext.getBean(BundleClassLoaderHolder.class).getBundleClassLoader(bundleContext.getBundleID())
                                             .getResource("external_files.7z");
            HardwareUtils.copyResources(externalFiles);

            entityContext.rebuildAllRepositories(context, true);
            entityContext.updateBeans(bundleContext, context, true);

            for (BundleEntrypoint bundleEntrypoint :
                context.getBeansOfType(BundleEntrypoint.class).values()) {
                log.info("Init bundle: <{}>", bundleEntrypoint.getBundleId());
                bundleEntrypoint.init();
                this.bundles.put(bundleEntrypoint.getBundleId(), new InternalBundleContext(bundleEntrypoint, bundleContext));
            }
        }
    }

    private @Nullable List<String> getAddonReleasesSince(String bundleID, String version) {
        var bundleManager = new SystemBundleLibraryManagerSetting();
        PackageContext allPackages = bundleManager.allPackages(entityContext);
        PackageModel packageModel = allPackages.getPackages().stream().filter(p -> p.getName().equals(bundleID)).findAny().orElse(null);
        if (packageModel == null) {
            log.error("Unable to find addon '{}' in repositories", bundleID);
        } else {
            return GitHubProject.getReleasesSince(version, packageModel.getVersions(), false);
        }
        return null;
    }

    public static class InternalBundleContext {

        @Getter private final BundleEntrypoint bundleEntrypoint;
        @Getter private final BundleContext bundleContext;
        private final Map<String, Object> fieldTypes = new HashMap<>();

        public InternalBundleContext(BundleEntrypoint bundleEntrypoint, BundleContext bundleContext) {
            this.bundleEntrypoint = bundleEntrypoint;
            this.bundleContext = bundleContext;
            if (bundleContext != null) {
                for (WidgetBaseTemplate widgetBaseTemplate :
                    bundleContext
                        .getApplicationContext()
                        .getBeansOfType(WidgetBaseTemplate.class)
                        .values()) {
                    fieldTypes.put(widgetBaseTemplate.getClass().getSimpleName(), widgetBaseTemplate);
                }
            }
        }
    }
}

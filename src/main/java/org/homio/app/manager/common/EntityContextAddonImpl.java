package org.homio.app.manager.common;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextUI.NotificationBlockBuilder;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel;
import org.homio.api.repository.GitHubProject;
import org.homio.api.setting.SettingPluginPackageInstall.PackageContext;
import org.homio.api.setting.SettingPluginPackageInstall.PackageModel;
import org.homio.api.setting.SettingPluginPackageInstall.PackageRequest;
import org.homio.api.ui.UI.Color;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.api.widget.WidgetBaseTemplate;
import org.homio.app.HomioClassLoader;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.CacheService;
import org.homio.app.setting.system.SystemAddonLibraryManagerSetting;
import org.homio.app.utils.HardwareUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

@Log4j2
@RequiredArgsConstructor
public class EntityContextAddonImpl {

    // count how much add/remove addon invokes
    public static int ADDON_UPDATE_COUNT = 0;
    @Getter
    private final Map<String, InternalAddonContext> addons = new LinkedHashMap<>();
    private final EntityContextImpl entityContext;
    private final CacheService cacheService;
    @Setter
    private ApplicationContext applicationContext;

    public void initializeInlineAddons() {
        log.info("Initialize addons...");
        ArrayList<AddonEntrypoint> addonEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(AddonEntrypoint.class).values());
        Collections.sort(addonEntrypoints);
        for (AddonEntrypoint entrypoint : addonEntrypoints) {
            this.addons.put("addon-" + entrypoint.getAddonID(), new InternalAddonContext(entrypoint, null));
            log.info("Initialize addon: <{}>", entrypoint.getAddonID());
            try {
                entrypoint.init();
                entrypoint.onContextRefresh();
                log.info("Done initialize addon: <{}>", entrypoint.getAddonID());
            } catch (Exception ex) {
                log.fatal("Unable to initialize addon: " + entrypoint.getAddonID(), ex);
                throw ex;
            }
        }
        log.info("Done initialize addons");
    }

    public void addAddons(Map<String, AddonContext> artifactIdContextMap) {
        Map<String, ApplicationContext> contexts = new HashMap<>();
        for (String artifactId : artifactIdContextMap.keySet()) {
            ApplicationContext context = this.addAddons(artifactIdContextMap.get(artifactId));
            if (context != null) {
                contexts.put(artifactId, context);
            }
        }
        if (contexts.isEmpty()) {
            return;
        }
        this.cacheService.clearCache();

        for (Entry<String, ApplicationContext> entry : contexts.entrySet()) {
            ApplicationContext context = entry.getValue();
            AddonContext addonContext = artifactIdContextMap.get(entry.getKey());

            entityContext.rebuildRepositoryByPrefixMap();
            entityContext.updateBeans(addonContext, context, true);

            for (AddonEntrypoint addonEntrypoint : context.getBeansOfType(AddonEntrypoint.class).values()) {
                log.info("Init addon: <{}>", addonEntrypoint.getAddonID());
                addonEntrypoint.init();
                this.addons.put("addon-" + addonEntrypoint.getAddonID(), new InternalAddonContext(addonEntrypoint, addonContext));
            }
        }
        applicationContext.getBean(TransactionManagerContext.class).invalidate();

        ADDON_UPDATE_COUNT++;
    }

    public void removeAddon(String addonID) {
        InternalAddonContext internalAddonContext = addons.remove(addonID);
        AddonContext addonContext = internalAddonContext.addonContext;
        ApplicationContext context = addonContext.getApplicationContext();
        // refresh collections to remove beans from removing addon
        entityContext.updateBeans(addonContext, addonContext.getApplicationContext(), false);
        context.getBean(AddonEntrypoint.class).destroy();

        addonContext.getConfig().destroy();
        entityContext.getAllApplicationContexts().remove(context);
        HomioClassLoader.removeClassLoader(addonID);

        cacheService.clearCache();
        entityContext.rebuildRepositoryByPrefixMap();
        entityContext.ui().updateNotificationBlock("addons", builder -> builder.removeInfo(addonID));

        ADDON_UPDATE_COUNT++;
    }

    public Object getBeanOfAddonsBySimpleName(String addonID, String className) {
        InternalAddonContext internalAddonContext = this.addons.get("addon-" + addonID);
        if (internalAddonContext == null) {
            throw new NotFoundException("Unable to find addon <" + addonID + ">");
        }
        Object o = internalAddonContext.fieldTypes.get(className);
        if (o == null) {
            throw new NotFoundException("Unable to find class <" + className + "> in addon <" + addonID + ">");
        }
        return o;
    }

    private @Nullable ApplicationContext addAddons(AddonContext addonContext) {
        if (!addonContext.isInternal() && !addonContext.isInstalled()) {
            String key = addonContext.getAddonID();

            PackageModel packageModel = getPackageModel(addonContext.getPomFile().getArtifactId());
            if (packageModel != null) {
                entityContext.ui().updateNotificationBlock("addons",
                    builder -> addAddonNotificationRow(addonContext, key, packageModel, builder));
            }

            if (!addonContext.isLoaded()) {
                return null;
            }

            entityContext.getAllApplicationContexts().add(addonContext.getApplicationContext());
            addonContext.setInstalled(true);

            ApplicationContext context = addonContext.getApplicationContext();
            URL externalFiles = HomioClassLoader.getJarClassLoader(key).getResource("external_files.7z");
            HardwareUtils.copyResources(externalFiles);
            return context;
        }
        return null;
    }

    private void addAddonNotificationRow(@NotNull AddonContext addonContext, @NotNull String key,
        @NotNull PackageModel packageModel, @NotNull NotificationBlockBuilder builder) {
        String icon = addonContext.isLoaded() ? "fas fa-puzzle-piece" : "fas fa-bug";
        String color = addonContext.isLoaded() ? Color.GREEN : Color.RED;
        String info = addonContext.getAddonFriendlyName();
        List<String> versions = GitHubProject.getReleasesSince(addonContext.getVersion(), packageModel.getVersions(), false);
        if (versions.isEmpty()) {
            builder.addInfo(key, info, null, icon, color, addonContext.getVersion(), null);
        } else {
            builder.addFlexAction(key, flex -> {
                flex.addInfo(info).setIcon(icon, color);
                flex.addSelectBox("versions", (entityContext, params) ->
                        handleUpdateAddon(addonContext, key, entityContext, params, versions, packageModel))
                    .setHighlightSelected(false)
                    .setOptions(OptionModel.list(versions))
                    .setAsButton("fas fa-cloud-download-alt", Color.PRIMARY_COLOR, addonContext.getVersion())
                    .setHeight(20).setPrimary(true);
            });
        }
    }

    @Nullable
    private ActionResponseModel handleUpdateAddon(@NotNull AddonContext addonContext, @NotNull String key,
        @NotNull EntityContext entityContext, @NotNull JSONObject params, @NotNull List<String> versions,
        @NotNull PackageModel packageModel) {
        String newVersion = params.getString("value");
        if (versions.contains(newVersion)) {
            String question = Lang.getServerMessage("PACKAGE_UPDATE_QUESTION",
                FlowMap.of("NAME", addonContext.getPomFile().getName(), "VERSION", newVersion));
            entityContext.ui().sendConfirmation("update-" + key, "DIALOG.TITLE.UPDATE_PACKAGE", () ->
                entityContext.getBean(AddonService.class).installPackage(
                    new SystemAddonLibraryManagerSetting(),
                    new PackageRequest().setName(packageModel.getName())
                                        .setVersion(newVersion)
                                        .setUrl(packageModel.getJarUrl())), Collections.singletonList(question), null);
            return null;
        }
        return ActionResponseModel.showError("Unable to find package or version");
    }

    private @Nullable PackageModel getPackageModel(String addonID) {
        var addonManager = new SystemAddonLibraryManagerSetting();
        PackageContext allPackages = addonManager.allPackages(entityContext);
        PackageModel packageModel = allPackages.getPackages().stream().filter(p -> p.getName().equals(addonID)).findAny().orElse(null);
        if (packageModel == null) {
            log.error("Unable to find addon '{}' in repositories", addonID);
        } else {
            return packageModel;
        }
        return null;
    }

    public static class InternalAddonContext {

        @Getter private final AddonEntrypoint addonEntrypoint;
        @Getter private final AddonContext addonContext;
        private final Map<String, Object> fieldTypes = new HashMap<>();

        public InternalAddonContext(AddonEntrypoint addonEntrypoint, AddonContext addonContext) {
            this.addonEntrypoint = addonEntrypoint;
            this.addonContext = addonContext;
            if (addonContext != null) {
                for (WidgetBaseTemplate widgetBaseTemplate :
                    addonContext
                        .getApplicationContext()
                        .getBeansOfType(WidgetBaseTemplate.class)
                        .values()) {
                    fieldTypes.put(widgetBaseTemplate.getClass().getSimpleName(), widgetBaseTemplate);
                }
            }
        }
    }
}

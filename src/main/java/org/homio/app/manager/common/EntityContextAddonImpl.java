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
import org.homio.api.ui.UI.Color;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.api.widget.WidgetBaseTemplate;
import org.homio.app.HomioClassLoader;
import org.homio.app.config.TransactionManagerImpl;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.CacheService;
import org.homio.app.setting.system.SystemAddonLibraryManagerSetting;
import org.homio.app.utils.HardwareUtils;
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

    public void initialiseInlineAddons() {
        log.info("Initialize addons...");
        ArrayList<AddonEntrypoint> addonEntrypoints = new ArrayList<>(applicationContext.getBeansOfType(AddonEntrypoint.class).values());
        Collections.sort(addonEntrypoints);
        for (AddonEntrypoint entrypoint : addonEntrypoints) {
            this.addons.put(entrypoint.getAddonId(), new InternalAddonContext(entrypoint, null));
            log.info("Init addon: <{}>", entrypoint.getAddonId());
            try {
                entrypoint.init();
                entrypoint.onContextRefresh();
            } catch (Exception ex) {
                log.fatal("Unable to init addon: " + entrypoint.getAddonId(), ex);
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
                log.info("Init addon: <{}>", addonEntrypoint.getAddonId());
                addonEntrypoint.init();
                this.addons.put(addonEntrypoint.getAddonId(), new InternalAddonContext(addonEntrypoint, addonContext));
            }
        }
        applicationContext.getBean(TransactionManagerImpl.class).invalidate();

        ADDON_UPDATE_COUNT++;
    }

    public void removeAddon(String addonID) {
        InternalAddonContext internalAddonContext = addons.remove(addonID);
        if (internalAddonContext != null) {
            this.removeAddon(internalAddonContext.addonContext);
        }
    }

    public Object getBeanOfAddonsBySimpleName(String addon, String className) {
        InternalAddonContext internalAddonContext = this.addons.get(addon);
        if (internalAddonContext == null) {
            throw new NotFoundException("Unable to find addon <" + addon + ">");
        }
        Object o = internalAddonContext.fieldTypes.get(className);
        if (o == null) {
            throw new NotFoundException("Unable to find class <" + className + "> in addon <" + addon + ">");
        }
        return o;
    }

    private void removeAddon(AddonContext addonContext) {
        if (!addonContext.isInternal() && addonContext.isInstalled()) {
            ApplicationContext context = addonContext.getApplicationContext();
            context.getBean(AddonEntrypoint.class).destroy();
            entityContext.getAllApplicationContexts().remove(context);

            this.cacheService.clearCache();

            entityContext.rebuildRepositoryByPrefixMap();
            entityContext.updateBeans(addonContext, addonContext.getApplicationContext(), false);
            ADDON_UPDATE_COUNT++;
        }
    }

    private ApplicationContext addAddons(AddonContext addonContext) {
        if (!addonContext.isInternal() && !addonContext.isInstalled()) {
            entityContext.ui().addNotificationBlockOptional("addons", "Addons", "fas fa-file-zipper", "#FF4400");
            String key = addonContext.getAddonID();

            List<String> versions = getAddonReleasesSince(addonContext.getPomFile().getArtifactId(), addonContext.getVersion());
            entityContext.ui().updateNotificationBlock("addons",
                builder -> addAddonNotificationRow(addonContext, key, versions, builder));

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

    private void addAddonNotificationRow(AddonContext addonContext, String key, List<String> versions,
        NotificationBlockBuilder builder) {
        String icon = addonContext.isLoaded() ? "fas fa-puzzle-piece" : "fas fa-bug";
        String color = addonContext.isLoaded() ? Color.GREEN : Color.RED;
        String info = addonContext.getAddonFriendlyName();
        if (versions == null || versions.isEmpty()) {
            builder.addInfo(key, info, null, icon, color, addonContext.getVersion(), null);
        } else {
            builder.addFlexAction(key, flex -> {
                flex.addInfo(info).setIcon(icon, color);
                flex.addSelectBox("versions", (entityContext, params) ->
                        handleUpdateAddon(addonContext, key, entityContext, params, versions))
                    .setHighlightSelected(false)
                    .setOptions(OptionModel.list(versions))
                    .setAsButton("fas fa-cloud-download-alt", Color.PRIMARY_COLOR, addonContext.getVersion())
                    .setHeight(20).setPrimary(true);
            });
        }
    }

    @Nullable
    private ActionResponseModel handleUpdateAddon(AddonContext addonContext, String key, EntityContext entityContext, JSONObject params,
        List<String> versions) {
        String newVersion = params.getString("value");
        if (versions.contains(newVersion)) {
            String question = Lang.getServerMessage("PACKAGE_UPDATE_QUESTION",
                FlowMap.of("NAME", addonContext.getPomFile().getName(), "VERSION", newVersion));
            entityContext.ui().sendConfirmation("update-" + key, "DIALOG.TITLE.UPDATE_PACKAGE", new Runnable() {
                @Override
                public void run() {
                    System.out.println("GGGG");
                }
            }, Collections.singletonList(question), null);
        }
        return null;
    }

    private @Nullable List<String> getAddonReleasesSince(String addonID, String version) {
        var addonManager = new SystemAddonLibraryManagerSetting();
        PackageContext allPackages = addonManager.allPackages(entityContext);
        PackageModel packageModel = allPackages.getPackages().stream().filter(p -> p.getName().equals(addonID)).findAny().orElse(null);
        if (packageModel == null) {
            log.error("Unable to find addon '{}' in repositories", addonID);
        } else {
            return GitHubProject.getReleasesSince(version, packageModel.getVersions(), false);
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

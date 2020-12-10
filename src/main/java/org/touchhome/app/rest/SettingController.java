package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.BundleManager;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.utils.Curl;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.setting.BundlePackageInstallSettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.setting.header.dynamic.BundleHeaderDynamicContainerSettingPlugin;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rest/setting")
@RequiredArgsConstructor
public class SettingController {

    private final ConsoleController consoleController;
    private final BundleManager bundleManager;

    private Map<String, Set<String>> settingToPages;
    private Set<SettingEntity> descriptionSettings;

    private EntityContextImpl entityContext;
    private Map<Class<? extends BundleSettingPlugin<?>>, SettingEntity> transientSettings;

    public void postConstruct(EntityContextImpl entityContext) {
        this.entityContext = entityContext;
        SettingRepository settingRepository = entityContext.getBean(SettingRepository.class);
        settingRepository.postConstruct();

        this.transientSettings = new HashMap<>();
        for (BundleSettingPlugin<?> settingPlugin : EntityContextSettingImpl.settingPluginsByPluginKey.values()) {
            if (settingPlugin.transientState()) {
                this.transientSettings.put((Class<? extends BundleSettingPlugin<?>>) settingPlugin.getClass(),
                        SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity(), entityContext));
            }
            if (settingPlugin instanceof BundleConsoleSettingPlugin && !settingPlugin.getClass().getSimpleName().startsWith("Console")) {
                throw new RuntimeException("Console plugin class <" + settingPlugin.getClass().getName() + "> must starts with name 'Console'");
            }
        }
    }

    @GetMapping("{entityID}/options")
    public Collection<Option> loadSettingAvailableValues(@PathVariable("entityID") String entityID) {
        BundleSettingPlugin<?> bundleSettingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        return bundleSettingPlugin.loadAvailableValues(entityContext);
    }

    @GetMapping("{entityID}/packages/all")
    public Collection<BundlePackageInstallSettingPlugin.PackageEntity> loadAllPackages(@PathVariable("entityID") String entityID) throws Exception {
        BundleSettingPlugin<?> bundleSettingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (bundleSettingPlugin instanceof BundlePackageInstallSettingPlugin) {
            Collection<BundlePackageInstallSettingPlugin.PackageEntity> packages = ((BundlePackageInstallSettingPlugin) bundleSettingPlugin).allBundles(entityContext);
            for (Map.Entry<String, Boolean> entry : packagesInProgress.entrySet()) {
                BundlePackageInstallSettingPlugin.PackageEntity singlePackage = packages.stream().filter(p -> p.getName().equals(entry.getKey())).findFirst().orElse(null);
                if (singlePackage != null) {
                    if (entry.getValue()) {
                        singlePackage.setInstalling(true);
                    } else {
                        singlePackage.setRemoving(true);
                    }
                }
            }
            return packages;
        }
        return null;
    }

    @GetMapping("{entityID}/packages")
    public Collection<BundlePackageInstallSettingPlugin.PackageEntity> loadInstalledPackages(@PathVariable("entityID") String entityID) throws Exception {
        BundleSettingPlugin<?> bundleSettingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (bundleSettingPlugin instanceof BundlePackageInstallSettingPlugin) {
            return ((BundlePackageInstallSettingPlugin) bundleSettingPlugin).installedBundles(entityContext);
        }
        return null;
    }

    // true - installing, false removing
    private Map<String, Boolean> packagesInProgress = new ConcurrentHashMap<>();

    @DeleteMapping("{entityID}/packages")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void unInstallPackage(@PathVariable("entityID") String entityID,
                                 @RequestBody BundlePackageInstallSettingPlugin.PackageRequest packageRequest) {
        BundleSettingPlugin<?> bundleSettingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (bundleSettingPlugin instanceof BundlePackageInstallSettingPlugin) {
            if (!packagesInProgress.containsKey(packageRequest.getName())) {
                packagesInProgress.put(packageRequest.getName(), false);
                entityContext.ui().runWithProgress("Uninstall " + packageRequest.getName() + "/" + packageRequest.getVersion(),
                        key -> ((BundlePackageInstallSettingPlugin) bundleSettingPlugin).unInstall(entityContext, packageRequest, key),
                        () -> packagesInProgress.remove(packageRequest.getName()));
            }
        }
    }

    @PostMapping("{entityID}/packages")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void installPackage(@PathVariable("entityID") String entityID,
                               @RequestBody BundlePackageInstallSettingPlugin.PackageRequest packageRequest) {
        BundleSettingPlugin<?> bundleSettingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (bundleSettingPlugin instanceof BundlePackageInstallSettingPlugin) {
            if (!packagesInProgress.containsKey(packageRequest.getName())) {
                packagesInProgress.put(packageRequest.getName(), true);
                entityContext.ui().runWithProgress("Install " + packageRequest.getName() + "/" + packageRequest.getVersion(),
                        key -> ((BundlePackageInstallSettingPlugin) bundleSettingPlugin).install(entityContext, packageRequest, key),
                        () -> packagesInProgress.remove(packageRequest.getName()));
            }
        }
    }

    @Secured(TouchHomeUtils.ADMIN_ROLE)
    @PostMapping(value = "{entityID}", consumes = "text/plain")
    public <T> void updateSetting(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
        BundleSettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin != null) {
            entityContext.setting().setValueRaw((Class<? extends BundleSettingPlugin<T>>) settingPlugin.getClass(), value, false);
        }
    }

    @GetMapping("name")
    public List<Option> getSettingNames() {
        return Option.list(entityContext.findAll(SettingEntity.class));
    }

    @GetMapping
    public List<SettingEntity> getSettings() {
        List<SettingEntity> settings = entityContext.findAll(SettingEntity.class);
        for (Map.Entry<Class<? extends BundleSettingPlugin<?>>, SettingEntity> entry : transientSettings.entrySet()) {
            SettingEntity settingEntity = entry.getValue();
            settingEntity.setValue(entityContext.setting().getRawValue((Class) entry.getKey()));
            SettingRepository.fulfillEntityFromPlugin(settingEntity, entityContext, null);
            if (BundleHeaderDynamicContainerSettingPlugin.class.isAssignableFrom(entry.getKey())) {
                settingEntity.setSettingTypeRaw("Container");
                List<SettingEntity> options = EntityContextSettingImpl.dynamicHeaderSettings.get(entry.getKey());
                settingEntity.getParameters().put("dynamicOptions", options);
            } else if (BundlePackageInstallSettingPlugin.class.isAssignableFrom(entry.getKey())) {
                settingEntity.setSettingTypeRaw("BundleInstaller");
            }
            settings.add(settingEntity);
        }

        UserEntity userEntity = entityContext.getUser();

        if (settingToPages == null) {
            settingToPages = new HashMap<>();
            this.updateSettingToPages(settings);
        }

        for (Iterator<SettingEntity> iterator = settings.iterator(); iterator.hasNext(); ) {
            SettingEntity settingEntity = iterator.next();
            BundleSettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin != null) {

                // fulfill pages
                if (settingToPages.containsKey(settingEntity.getEntityID())) {
                    settingEntity.setPages(settingToPages.get(settingEntity.getEntityID()));
                }

                // hide secured values if requires
                if (plugin.isSecuredValue() && !userEntity.isAdmin()) {
                    settingEntity.setValue("********");
                }
                settingEntity.setVisible(plugin.isVisible(entityContext));
            } else {
                iterator.remove();
            }
        }

        if (descriptionSettings == null) {
            descriptionSettings = new HashSet<>();
            updateSettingDescription(settings);
        }

        settings.addAll(descriptionSettings);
        Collections.sort(settings);
        return settings;
    }

    private void updateSettingToPages(List<SettingEntity> settings) {
        // fulfill console pages
        for (SettingEntity settingEntity : settings) {
            BundleSettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin instanceof BundleConsoleSettingPlugin) {
                for (Map.Entry<String, ConsolePlugin<?>> entry : consoleController.getConsolePluginsMap().entrySet()) {
                    if (((BundleConsoleSettingPlugin<?>) plugin).acceptConsolePluginPage(entry.getValue())) {
                        settingToPages.computeIfAbsent(settingEntity.getEntityID(),
                                s -> new HashSet<>()).add(StringUtils.defaultString(entry.getValue().getParentTab(), entry.getKey()));
                    }
                }
            }
        }
    }

    /**
     * add setting descriptions
     */
    private void updateSettingDescription(List<SettingEntity> settings) {
        Set<String> bundleSettings = settings.stream().map(e -> {
            BundleSettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(e.getEntityID());
            return SettingRepository.getSettingBundleName(entityContext, plugin.getClass());
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        for (BundleEntryPoint bundleEntrypoint : bundleManager.getBundles()) {
            if (bundleSettings.contains(bundleEntrypoint.getBundleId())) {
                // find if description exists inside lang.json
                String descriptionKey = bundleEntrypoint.getBundleId() + ".setting.description";
                String description = En.findPathText(descriptionKey);
                if (description != null) {
                    this.descriptionSettings.add(new SettingEntity().setSettingType(BundleSettingPlugin.SettingType.Description)
                            .setBundle(bundleEntrypoint.getBundleId())
                            .setEntityID(SettingEntity.PREFIX + bundleEntrypoint.getBundleId() + "_Description")
                            .setValue(descriptionKey).setOrder(1));
                }
            }
        }
    }
}

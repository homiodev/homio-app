package org.homio.app.rest;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.app.model.entity.SettingEntity.getKey;
import static org.homio.app.repository.SettingRepository.fulfillEntityFromPlugin;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingPluginOptionsRemovable;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.setting.SettingType;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.homio.api.util.Lang;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.repository.SettingRepository;
import org.homio.app.spring.ContextRefreshed;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/rest/setting", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SettingController implements ContextRefreshed {

    private final AddonService addonService;
    private final EntityContextImpl entityContext;
    private Map<String, Set<String>> settingToPages;
    private Set<SettingEntity> descriptionSettings;
    private Map<Class<? extends SettingPlugin<?>>, SettingEntity> transientSettings;

    @Override
    public void onContextRefresh(EntityContext entityContext) {
        this.transientSettings = new HashMap<>();
        for (SettingPlugin<?> settingPlugin : EntityContextSettingImpl.settingPluginsByPluginKey.values()) {
            if (settingPlugin.transientState()) {
                SettingEntity entity = new SettingEntity();
                entity.setEntityID(getKey(settingPlugin));
                this.transientSettings.put((Class<? extends SettingPlugin<?>>) settingPlugin.getClass(), entity);
            }
            if (settingPlugin instanceof ConsoleSettingPlugin
                    && !settingPlugin.getClass().getSimpleName().startsWith("Console")) {
                throw new ServerException("Console plugin class <" + settingPlugin.getClass().getName() + "> must starts with name 'Console'");
            }
        }
    }

    @GetMapping("/{entityID}/options")
    public Collection<OptionModel> loadSettingAvailableValues(@PathVariable("entityID") String entityID,
                                                              @RequestParam(value = "param0", required = false) String param0) {
        SettingPluginOptions<?> settingPlugin = (SettingPluginOptions<?>) EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        return SettingRepository.getOptions(settingPlugin, entityContext, new JSONObject().put("param0", param0));
    }

    @GetMapping("/{entityID}/package/all")
    public SettingPluginPackageInstall.PackageContext loadAllPackages(@PathVariable("entityID") String entityID) throws Exception {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin instanceof SettingPluginPackageInstall) {
            SettingPluginPackageInstall.PackageContext packageContext =
                    ((SettingPluginPackageInstall) settingPlugin).allPackages(entityContext);
            for (Map.Entry<String, Boolean> entry : addonService.getPackagesInProgress().entrySet()) {
                SettingPluginPackageInstall.PackageModel singlePackage =
                        packageContext.getPackages().stream()
                                .filter(p -> p.getName().equals(entry.getKey()))
                                .findFirst()
                                .orElse(null);
                if (singlePackage != null) {
                    if (entry.getValue()) {
                        singlePackage.setInstalling(true);
                    } else {
                        singlePackage.setRemoving(true);
                    }
                }
            }
            return packageContext;
        }
        return null;
    }

    @GetMapping("/{entityID}/package")
    public SettingPluginPackageInstall.PackageContext loadInstalledPackages(@PathVariable("entityID") String entityID) throws Exception {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin instanceof SettingPluginPackageInstall) {
            return ((SettingPluginPackageInstall) settingPlugin).installedPackages(entityContext);
        }
        return null;
    }

    @DeleteMapping("/{entityID}/package")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void unInstallPackage(@PathVariable("entityID") String entityID, @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin instanceof SettingPluginPackageInstall) {
            addonService.unInstallPackage((SettingPluginPackageInstall) settingPlugin, packageRequest);
        }
    }

    @PostMapping("/{entityID}/package")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void installPackage(@PathVariable("entityID") String entityID, @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin instanceof SettingPluginPackageInstall) {
            addonService.installPackage((SettingPluginPackageInstall) settingPlugin, packageRequest);
        }
    }

    @SneakyThrows
    @PostMapping(value = "/{entityID}", consumes = "text/plain")
    public <T> void updateSetting(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin != null) {
            settingPlugin.assertUserAccess(entityContext, entityContext.getUser());
            entityContext.setting().setValueRaw((Class<? extends SettingPlugin<T>>) settingPlugin.getClass(), value, false);
        }
    }

    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    @DeleteMapping(value = "/{entityID}", consumes = "text/plain")
    public void removeSettingValue(@PathVariable("entityID") String entityID, @RequestBody String value) throws Exception {
        SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin instanceof SettingPluginOptionsRemovable) {
            ((SettingPluginOptionsRemovable<?>) settingPlugin).removeOption(entityContext, value);
        }
    }

    @GetMapping("/name")
    public List<OptionModel> getSettingNames() {
        return OptionModel.entityList(entityContext.findAll(SettingEntity.class));
    }

    public List<SettingEntity> getSettings() {
        List<SettingEntity> settings = entityContext.findAll(SettingEntity.class);
        assembleTransientSettings(settings);
        for (SettingEntity setting : settings) {
            fulfillEntityFromPlugin(setting, entityContext, null);
        }

        boolean isAdmin = entityContext.isAdmin();

        if (settingToPages == null) {
            settingToPages = new HashMap<>();
            this.updateSettingToPages(settings);
        }

        for (Iterator<SettingEntity> iterator = settings.iterator(); iterator.hasNext(); ) {
            SettingEntity settingEntity = iterator.next();
            SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(
                    settingEntity.getEntityID());
            if (plugin != null) {

                // fulfill pages
                if (settingToPages.containsKey(settingEntity.getEntityID())) {
                    settingEntity.setPages(settingToPages.get(settingEntity.getEntityID()));
                }

                // hide secured values if requires
                if (plugin.isSecuredValue() && !isAdmin) {
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

    private void assembleTransientSettings(List<SettingEntity> settings) {
        for (Map.Entry<Class<? extends SettingPlugin<?>>, SettingEntity> entry :
                transientSettings.entrySet()) {
            SettingEntity settingEntity = entry.getValue();
            settingEntity.setValue(entityContext.setting().getRawValue((Class) entry.getKey()));
            if (DynamicConsoleHeaderContainerSettingPlugin.class.isAssignableFrom(entry.getKey())) {
                settingEntity.setSettingTypeRaw("Container");
                List<SettingEntity> options = EntityContextSettingImpl.dynamicHeaderSettings.get(entry.getKey());
                settingEntity.getParameters().put("dynamicOptions", options);
            } else if (SettingPluginPackageInstall.class.isAssignableFrom(entry.getKey())) {
                settingEntity.setSettingTypeRaw("AddonInstaller");
            }
            for (String key : settingEntity.getJsonData().keySet()) {
                settingEntity.getParameters().put(key, settingEntity.getJsonData().get(key));
            }

            settings.add(settingEntity);
        }
    }

    private void updateSettingToPages(List<SettingEntity> settings) {
        // fulfill console pages
        for (SettingEntity settingEntity : settings) {
            SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin instanceof ConsoleSettingPlugin) {
                for (Map.Entry<String, ConsolePlugin<?>> entry :
                        EntityContextUIImpl.consolePluginsMap.entrySet()) {
                    if (((ConsoleSettingPlugin<?>) plugin)
                            .acceptConsolePluginPage(entry.getValue())) {
                        settingToPages
                                .computeIfAbsent(settingEntity.getEntityID(), s -> new HashSet<>())
                                .add(StringUtils.defaultString(entry.getValue().getParentTab(), entry.getKey()));
                    }
                }
            }
        }
    }

    /**
     * add setting descriptions
     */
    private void updateSettingDescription(List<SettingEntity> settings) {
        Set<String> addonSettings =
                settings.stream().map(e -> {
                            SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(e.getEntityID());
                            return SettingRepository.getSettingAddonName(entityContext, plugin.getClass());
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        for (AddonEntrypoint addonEntrypoint : addonService.getAddons()) {
            if (addonSettings.contains(addonEntrypoint.getAddonID())) {
                // find if description exists inside lang.json
                String descriptionKey = addonEntrypoint.getAddonID() + ".setting.description";
                String description = Lang.findPathText(descriptionKey);
                if (description != null) {
                    SettingEntity settingEntity = new SettingEntity() {
                        @Override
                        public String getAddonID() {
                            return addonEntrypoint.getAddonID();
                        }
                    };
                    settingEntity
                            .setSettingType(SettingType.Description)
                            .setVisible(true)
                            .setValue(descriptionKey)
                            .setOrder(1);
                    settingEntity.setEntityID(SettingEntity.PREFIX + addonEntrypoint.getAddonID() + "_Description");
                    this.descriptionSettings.add(settingEntity);
                }
            }
        }
    }
}

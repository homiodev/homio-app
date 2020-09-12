package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.BundleManager;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rest/setting")
@RequiredArgsConstructor
public class SettingController {

    private final ConsoleController consoleController;
    private final BundleManager bundleManager;

    private Map<String, Set<String>> settingToPages;
    private Set<SettingEntity> descriptionSettings;

    private EntityContext entityContext;
    private Map<Class<? extends BundleSettingPlugin>, SettingEntity> transientSettings;

    public void postConstruct(EntityContext entityContext) {
        this.entityContext = entityContext;
        SettingRepository settingRepository = entityContext.getBean(SettingRepository.class);
        settingRepository.postConstruct();

        this.transientSettings = new HashMap<>();
        for (BundleSettingPlugin settingPlugin : EntityContextImpl.settingPluginsByPluginKey.values()) {
            if (settingPlugin.transientState()) {
                this.transientSettings.put(settingPlugin.getClass(),
                        SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity(), entityContext));
            }
        }
    }

    @GetMapping("{entityID}/options")
    public List<Option> loadSettingAvailableValues(@PathVariable("entityID") String entityID) {
        return EntityContextImpl.settingPluginsByPluginKey.get(entityID).loadAvailableValues(entityContext);
    }

    @Secured(TouchHomeUtils.ADMIN_ROLE)
    @PostMapping(value = "{entityID}", consumes = "text/plain")
    public <T> void updateSettings(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
        BundleSettingPlugin settingPlugin = EntityContextImpl.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin != null) {
            entityContext.setSettingValueRaw((Class<? extends BundleSettingPlugin<T>>) settingPlugin.getClass(), value);
        }
    }

    @GetMapping("name")
    public List<Option> getSettingNames() {
        return Option.list(entityContext.findAll(SettingEntity.class));
    }

    @GetMapping
    public List<SettingEntity> getSettings() {
        List<SettingEntity> settings = entityContext.findAll(SettingEntity.class);
        for (Map.Entry<Class<? extends BundleSettingPlugin>, SettingEntity> entry : transientSettings.entrySet()) {
            SettingEntity settingEntity = entry.getValue();
            settingEntity.setValue(String.valueOf(entityContext.getSettingValue((Class) entry.getKey())));
            SettingRepository.fulfillEntityFromPlugin(settingEntity, entityContext);
            settings.add(settingEntity);
        }

        UserEntity userEntity = entityContext.getUser();

        if (settingToPages == null) {
            settingToPages = new HashMap<>();
            this.updateSettingToPages(settings);
        }

        for (Iterator<SettingEntity> iterator = settings.iterator(); iterator.hasNext(); ) {
            SettingEntity settingEntity = iterator.next();
            BundleSettingPlugin plugin = EntityContextImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin != null && plugin.isVisible(entityContext)) {

                // fulfill pages
                if (settingToPages.containsKey(settingEntity.getEntityID())) {
                    settingEntity.setPages(settingToPages.get(settingEntity.getEntityID()));
                }

                // hide secured values if requires
                if (plugin.isSecuredValue() && !userEntity.isAdmin()) {
                    settingEntity.setValue("********");
                }
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
            BundleSettingPlugin plugin = EntityContextImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin instanceof BundleConsoleSettingPlugin) {
                for (Map.Entry<String, ConsolePlugin> entry : consoleController.getConsolePluginsMap().entrySet()) {
                    if (((BundleConsoleSettingPlugin) plugin).acceptConsolePluginPage(entry.getValue())) {
                        settingToPages.computeIfAbsent(settingEntity.getEntityID(), s -> new HashSet<>()).add(entry.getKey());
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
            BundleSettingPlugin plugin = EntityContextImpl.settingPluginsByPluginKey.get(e.getEntityID());
            return SettingRepository.getSettingBundleName(entityContext, plugin.getClass());
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        for (BundleEntrypoint bundleEntrypoint : bundleManager.getBundles()) {
            if (bundleSettings.contains(bundleEntrypoint.getBundleId())) {
                // find if description exists inside lang.json
                String descriptionKey = bundleEntrypoint.getBundleId() + ".setting.description";
                String description = En.get().findPathText(descriptionKey);
                if (description != null) {
                    this.descriptionSettings.add(new SettingEntity().setSettingType(BundleSettingPlugin.SettingType.Description)
                            .setBundle(bundleEntrypoint.getBundleId())
                            .setEntityID("st_" + bundleEntrypoint.getBundleId() + "_Description")
                            .setValue(descriptionKey).setOrder(1));
                }
            }
        }
    }
}

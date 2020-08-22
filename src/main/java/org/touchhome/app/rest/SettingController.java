package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.*;

@RestController
@RequestMapping("/rest/setting")
@RequiredArgsConstructor
public class SettingController {

    private final ConsoleController consoleController;

    private EntityContext entityContext;
    private Map<Class<? extends BundleSettingPlugin>, SettingEntity> transientSettings;

    public void postConstruct(EntityContext entityContext) {
        this.entityContext = entityContext;
        SettingRepository settingRepository = entityContext.getBean(SettingRepository.class);
        settingRepository.postConstruct();

        this.transientSettings = new HashMap<>();
        for (BundleSettingPlugin settingPlugin : InternalManager.settingPluginsByPluginKey.values()) {
            if (settingPlugin.transientState()) {
                this.transientSettings.put(settingPlugin.getClass(),
                        SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity(), entityContext));
            }
        }
    }

    @GetMapping("{entityID}/options")
    public List<Option> loadSettingAvailableValues(@PathVariable("entityID") String entityID) {
        return InternalManager.settingPluginsByPluginKey.get(entityID).loadAvailableValues(entityContext);
    }

    @Secured(TouchHomeUtils.ADMIN_ROLE)
    @PostMapping(value = "{entityID}", consumes = "text/plain")
    public <T> void updateSettings(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
        BundleSettingPlugin settingPlugin = InternalManager.settingPluginsByPluginKey.get(entityID);
        if (settingPlugin != null) {
            entityContext.setSettingValueRaw((Class<? extends BundleSettingPlugin<T>>) settingPlugin.getClass(), value);
        }
    }

    @GetMapping
    public List<SettingEntity> getSettings() {
        List<SettingEntity> result = entityContext.findAll(SettingEntity.class);
        for (Map.Entry<Class<? extends BundleSettingPlugin>, SettingEntity> entry : transientSettings.entrySet()) {
            result.add(entry.getValue().setValue(String.valueOf(entityContext.getSettingValue((Class) entry.getKey()))));
        }

        Set<String> pages = new HashSet<>();
        for (SettingEntity settingEntity : result) {
            BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(settingEntity.getEntityID());
            if (plugin instanceof BundleConsoleSettingPlugin) {
                for (Map.Entry<String, ConsolePlugin> entry : consoleController.getConsolePluginsMap().entrySet()) {
                    if (((BundleConsoleSettingPlugin) plugin).acceptConsolePluginPage(entry.getValue())) {
                        pages.add(entry.getKey());
                    }
                }
                if (!pages.isEmpty()) {
                    settingEntity.setPages(pages.toArray(new String[0]));
                    pages.clear();
                }
            }
        }

       /* List<String> refreshContentPages = new ArrayList<>();
        List<String> fitContentPages = new ArrayList<>();
        for (Map.Entry<String, ConsolePlugin> entry : consoleController.getConsolePluginsMap().entrySet()) {
            if (entry.getValue().hasRefreshIntervalSetting()) {
                refreshContentPages.add(entry.getKey());
            }
            if (entry.getValue().hasFitContentSetting()) {
                fitContentPages.add(entry.getKey());
            }
        }
        for (SettingEntity settingEntity : result) {
            if (settingEntity.getEntityID().equals(SettingEntity.PREFIX + ConsoleRefreshContentPeriodSetting.class.getSimpleName())) {
                settingEntity.setPages(refreshContentPages.toArray(new String[0]));
            } else if (settingEntity.getEntityID().equals(SettingEntity.PREFIX + ConsoleFitContentSetting.class.getSimpleName())) {
                settingEntity.setPages(fitContentPages.toArray(new String[0]));
            }
        }*/

        Collections.sort(result);
        return result;
    }
}

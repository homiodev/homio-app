package org.touchhome.app.rest;

import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rest/setting")
public class SettingController {

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
                        SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity()));
            }
        }
        settingRepository.deleteRemovedSettings();
    }

    @GetMapping("{entityID}/options")
    public List<Option> getSettingsAvailableItems(@PathVariable("entityID") String entityID) {
        return InternalManager.settingPluginsByPluginKey.get(entityID).loadAvailableValues(entityContext);
    }

    @PostMapping(value = "{entityID}", consumes = "text/plain")
    public <T> void updateSettings(@PathVariable("entityID") String entityID, @RequestBody String value) {
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
        Collections.sort(result);
        return result;
    }
}

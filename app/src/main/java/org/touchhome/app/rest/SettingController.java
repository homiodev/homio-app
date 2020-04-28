package org.touchhome.app.rest;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rest/setting")
@AllArgsConstructor
public class SettingController {

    private final EntityContext entityContext;
    private Map<String, BundleSettingPlugin> settingPlugins;
    private Map<Class<? extends BundleSettingPlugin>, SettingEntity> transientSettings;

    public void postConstruct(Map<String, BundleSettingPlugin> settingPlugins) {
        this.settingPlugins = settingPlugins;
        for (BundleSettingPlugin settingPlugin : settingPlugins.values()) {
            if (settingPlugin.transientState()) {
                this.transientSettings.put(settingPlugin.getClass(),
                        SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity()));
            }
        }
    }

    @GetMapping("{entityID}/options")
    public List<Option> getSettingsAvailableItems(@PathVariable("entityID") String entityID) {
        return settingPlugins.get(entityID).loadAvailableValues();
    }

    @PostMapping(value = "{entityID}", consumes = "text/plain")
    public <T> void updateSettings(@PathVariable("entityID") String entityID, @RequestBody String value) {
        BundleSettingPlugin settingPlugin = settingPlugins.get(entityID);
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

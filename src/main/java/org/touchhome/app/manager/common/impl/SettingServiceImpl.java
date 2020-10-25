package org.touchhome.app.manager.common.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class SettingServiceImpl {
    private static final Map<BundleSettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, BundleSettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    private static Map<String, BundleSettingPlugin> settingPluginsByPluginClass = new HashMap<>();

    private Map<String, Map<String, Consumer<?>>> settingListeners = new HashMap<>();

    private final EntityContextImpl entityContext;

    public <T> T getSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value = this.getSettingRawValue(settingPluginClazz);
        return pluginFor.parseValue(entityContext, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    }

    public <T> String getSettingRawValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        if (pluginFor.transientState()) {
            return settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingRepository.getKey(pluginFor));
            return settingEntity == null ? null : settingEntity.getValue();
        }
    }

    public <T> void listenSettingValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, String key, Consumer<T> listener) {
        settingListeners.putIfAbsent(bundleSettingPluginClazz.getName(), new HashMap<>());
        settingListeners.get(bundleSettingPluginClazz.getName()).put(key, listener);
    }

    public <T> void setSettingValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = setSettingValueSilence(settingPluginClazz, value);
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor, strValue);
    }

    public <T> String setSettingValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = pluginFor.writeValue(value);
        this.setSettingValueSilenceRaw(settingPluginClazz, strValue);
        return strValue;
    }

    public <T> void setSettingValueSilenceRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        log.debug("Update setting <{}> value <{}>", SettingRepository.getKey(pluginFor), value);

        if (pluginFor.transientState()) {
            settingTransientState.put(pluginFor, value);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingRepository.getKey(pluginFor));
            if (!Objects.equals(value, settingEntity.getValue())) {
                if (settingEntity.getDefaultValue().equals(value)) {
                    value = "";
                }
                entityContext.save(settingEntity.setValue(value));
            }
        }
    }

    public <T> void fireNotifySettingHandlers(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value,
                                              BundleSettingPlugin pluginFor, String strValue) {
        if (settingListeners.containsKey(settingPluginClazz.getName())) {
            for (Consumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
                try {
                    consumer.accept(value);
                } catch (Exception ex) {
                    log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value);
                }
            }
        }
        entityContext.getBroadcastLockManager().signalAll(SettingEntity.PREFIX + settingPluginClazz.getSimpleName(), StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue()));
        entityContext.sendNotification(pluginFor.buildToastrNotificationEntity(value, strValue, entityContext));
    }

    public <T> void notifySettingValueStateChanged(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        SettingEntity settingEntity = entityContext.getEntity(SettingRepository.getKey(pluginFor));
        T value = pluginFor.parseValue(entityContext, settingEntity.getValue());
        fireNotifySettingHandlers(settingPluginClazz, value, pluginFor, settingEntity.getValue());
    }

    @SneakyThrows
    public void updateSettingPlugins(Class<? extends BundleSettingPlugin> settingPlugin, boolean addBundle) {
        BundleSettingPlugin bundleSettingPlugin = settingPlugin.newInstance();
        String key = SettingRepository.getKey(bundleSettingPlugin);
        if (addBundle) {
            settingPluginsByPluginKey.put(key, bundleSettingPlugin);
            settingPluginsByPluginClass.put(settingPlugin.getName(), bundleSettingPlugin);
        } else {
            settingPluginsByPluginKey.remove(key);
            settingPluginsByPluginClass.remove(settingPlugin.getName());
            settingListeners.remove(settingPlugin.getName());
        }
    }

    public <T> void setSettingValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        setSettingValue(settingPluginClazz, (T) pluginFor.parseValue(entityContext, value));
    }
}

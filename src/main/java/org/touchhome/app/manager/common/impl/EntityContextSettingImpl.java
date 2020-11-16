package org.touchhome.app.manager.common.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class EntityContextSettingImpl implements EntityContextSetting {
    private static final Map<BundleSettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, BundleSettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    private static Map<String, BundleSettingPlugin> settingPluginsByPluginClass = new HashMap<>();
    private final EntityContextImpl entityContext;
    private Map<String, Map<String, Consumer<?>>> settingListeners = new HashMap<>();

    @Override
    public <T> T getValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value = this.getRawValue(settingPluginClazz);
        return pluginFor.parseValue(entityContext, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    }

    @Override
    public <T> String getRawValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        if (pluginFor.transientState()) {
            return settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
            return settingEntity == null ? null : settingEntity.getValue();
        }
    }

    @Override
    public <T> void listenValue(Class<? extends BundleSettingPlugin<T>> bundleSettingPluginClazz, String key, Consumer<T> listener) {
        settingListeners.putIfAbsent(bundleSettingPluginClazz.getName(), new HashMap<>());
        settingListeners.get(bundleSettingPluginClazz.getName()).put(key, listener);
    }

    @Override
    public <T> String setValueSilence(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = pluginFor.writeValue(value);
        this.setValueSilenceRaw(pluginFor, strValue);
        return strValue;
    }

    @Override
    public <T> void setValueSilenceRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        setValueSilenceRaw(settingPluginsByPluginClass.get(settingPluginClazz.getName()), value);
    }

    private <T> void fireNotifyHandlers(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value,
                                        BundleSettingPlugin pluginFor, String strValue, boolean fireUpdatesToUI) {
        if (settingListeners.containsKey(settingPluginClazz.getName())) {
            for (Consumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
                try {
                    consumer.accept(value);
                } catch (Exception ex) {
                    log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value);
                }
            }
        }
        entityContext.getBroadcastLockManager().signalAll(SettingEntity.getKey(settingPluginClazz),
                StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue()));
        if (fireUpdatesToUI) {
            entityContext.ui().sendNotification(pluginFor.buildToastrNotificationEntity(value, strValue, entityContext));
            entityContext.ui().sendNotification("-settings", new JSONObject().put("value", value)
                    .put("entityID", SettingEntity.getKey(settingPluginClazz)));
        }
    }

    /**
     * Fire notification that setting state was changed without writing actual value to db
     */
    public <T> void notifyValueStateChanged(Class<? extends BundleSettingPlugin<T>> settingPluginClazz) {
        BundleSettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
        T value = pluginFor.parseValue(entityContext, settingEntity.getValue());
        fireNotifyHandlers(settingPluginClazz, value, pluginFor, settingEntity.getValue(), true);
    }

    @SneakyThrows
    public void updatePlugins(Class<? extends BundleSettingPlugin> settingPlugin, boolean addBundle) {
        BundleSettingPlugin bundleSettingPlugin = settingPlugin.newInstance();
        String key = SettingEntity.getKey(bundleSettingPlugin);
        if (addBundle) {
            settingPluginsByPluginKey.put(key, bundleSettingPlugin);
            settingPluginsByPluginClass.put(settingPlugin.getName(), bundleSettingPlugin);
        } else {
            settingPluginsByPluginKey.remove(key);
            settingPluginsByPluginClass.remove(settingPlugin.getName());
            settingListeners.remove(settingPlugin.getName());
        }
    }

    @Override
    public <T> void setValue(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, T value) {
        BundleSettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = setValueSilence(settingPluginClazz, value);
        fireNotifyHandlers(settingPluginClazz, value, pluginFor, strValue, true);
    }

    @Override
    public <T> void setValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        setValueRaw(settingPluginClazz, value, true);
    }

    public <T> void setValueRaw(Class<? extends BundleSettingPlugin<T>> settingPluginClazz, @NotNull String value, boolean fireNotificationOnUI) {
        BundleSettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        T parsedValue = (T) pluginFor.parseValue(entityContext, value);
        String strValue = setValueSilence(pluginFor, parsedValue);
        fireNotifyHandlers(settingPluginClazz, parsedValue, pluginFor, strValue, fireNotificationOnUI);
    }

    private <T> String setValueSilence(BundleSettingPlugin pluginFor, @NotNull T value) {
        String strValue = pluginFor.writeValue(value);
        this.setValueSilenceRaw(pluginFor, strValue);
        return strValue;
    }

    private void setValueSilenceRaw(BundleSettingPlugin<?> pluginFor, @NotNull String value) {
        log.debug("Update setting <{}> value <{}>", SettingEntity.getKey(pluginFor), value);

        if (pluginFor.transientState()) {
            settingTransientState.put(pluginFor, value);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
            if (!Objects.equals(value, settingEntity.getValue())) {
                if (settingEntity.getDefaultValue().equals(value)) {
                    value = "";
                }
                entityContext.save(settingEntity.setValue(value));
            }
        }
    }
}

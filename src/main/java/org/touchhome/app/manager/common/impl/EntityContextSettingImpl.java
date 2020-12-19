package org.touchhome.app.manager.common.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginOptions;
import org.touchhome.bundle.api.setting.header.dynamic.DynamicHeaderContainerSettingPlugin;
import org.touchhome.bundle.api.setting.header.dynamic.DynamicHeaderSettingPlugin;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
public class EntityContextSettingImpl implements EntityContextSetting {
    private static final Map<SettingPlugin, String> settingTransientState = new HashMap<>();
    public static Map<String, SettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    public static Map<Class<? extends DynamicHeaderContainerSettingPlugin>, List<SettingEntity>> dynamicHeaderSettings = new HashMap<>();
    private static Map<String, SettingPlugin> settingPluginsByPluginClass = new HashMap<>();
    private final EntityContextImpl entityContext;
    private Map<String, Map<String, Consumer<?>>> settingListeners = new HashMap<>();

    @Override
    public void reloadSettings(Class<? extends SettingPluginOptions> settingPlugin) {
        String entityID = SettingEntity.getKey(settingPlugin);
        SettingPluginOptions<?> pluginOptions = (SettingPluginOptions<?>) EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);

        Collection<OptionModel> options = SettingRepository.getOptions(pluginOptions, entityContext);
        entityContext.ui().sendGlobal(EntityContextUI.GlobalSendType.setting, entityID, options, null,
                new JSONObject().put("subType", "list"));
    }

    @Override
    public void reloadSettings(Class<? extends DynamicHeaderContainerSettingPlugin> dynamicSettingPluginClass, List<? extends DynamicHeaderSettingPlugin> dynamicSettings) {
        List<SettingEntity> dynamicEntities = dynamicSettings.stream()
                .map(s -> SettingRepository.createSettingEntityFromPlugin(s, new SettingEntity(), entityContext)).collect(Collectors.toList());
        dynamicHeaderSettings.put(dynamicSettingPluginClass, dynamicEntities);

        entityContext.ui().sendGlobal(EntityContextUI.GlobalSendType.setting, SettingEntity.PREFIX + dynamicSettingPluginClass.getSimpleName(),
                dynamicEntities, null, new JSONObject().put("subType", "dynamic"));
    }

    @Override
    public <T> T getValue(Class<? extends SettingPlugin<T>> settingPluginClazz) {
        SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value = this.getRawValue(settingPluginClazz);
        return pluginFor.parseValue(entityContext, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    }

    @Override
    public <T> String getRawValue(Class<? extends SettingPlugin<T>> settingPluginClazz) {
        SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        if (pluginFor.transientState()) {
            return settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
            return settingEntity == null ? null : settingEntity.getValue();
        }
    }

    @Override
    public <T> void listenValueAsync(Class<? extends SettingPlugin<T>> settingClass, String key, Consumer<T> listener) {
        listenValue(settingClass, key, value ->
                this.entityContext.bgp().run(key, () -> listener.accept(value), true));
    }

    @Override
    public <T> void listenValue(Class<? extends SettingPlugin<T>> SettingPluginClazz, String key, Consumer<T> listener) {
        settingListeners.putIfAbsent(SettingPluginClazz.getName(), new HashMap<>());
        settingListeners.get(SettingPluginClazz.getName()).put(key, listener);
    }

    @Override
    public <T> String setValueSilence(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        SettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = pluginFor.writeValue(value);
        this.setValueSilenceRaw(pluginFor, strValue);
        return strValue;
    }

    @Override
    public <T> void setValueSilenceRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        setValueSilenceRaw(settingPluginsByPluginClass.get(settingPluginClazz.getName()), value);
    }

    private <T> void fireNotifyHandlers(Class<? extends SettingPlugin<T>> settingPluginClazz, T value,
                                        SettingPlugin pluginFor, String strValue, boolean fireUpdatesToUI) {
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
            entityContext.ui().sendGlobal(EntityContextUI.GlobalSendType.setting, SettingEntity.getKey(settingPluginClazz),
                    value, null, new JSONObject().put("subType", "single"));
        }
    }

    /**
     * Fire notification that setting state was changed without writing actual value to db
     */
    public <T> void notifyValueStateChanged(Class<? extends SettingPlugin<T>> settingPluginClazz) {
        SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
        T value = pluginFor.parseValue(entityContext, settingEntity.getValue());
        fireNotifyHandlers(settingPluginClazz, value, pluginFor, settingEntity.getValue(), true);
    }

    @SneakyThrows
    public void updatePlugins(Class<? extends SettingPlugin> settingPluginClass, boolean addBundle) {
        if (Modifier.isPublic(settingPluginClass.getModifiers())) {
            SettingPlugin settingPlugin = settingPluginClass.newInstance();
            String key = SettingEntity.getKey(settingPlugin);
            if (addBundle) {
                settingPluginsByPluginKey.put(key, settingPlugin);
                settingPluginsByPluginClass.put(settingPluginClass.getName(), settingPlugin);
            } else {
                settingPluginsByPluginKey.remove(key);
                settingPluginsByPluginClass.remove(settingPluginClass.getName());
                settingListeners.remove(settingPluginClass.getName());
            }
        }
    }

    @Override
    public <T> void setValue(Class<? extends SettingPlugin<T>> settingPluginClazz, T value) {
        SettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = setValueSilence(settingPluginClazz, value);
        fireNotifyHandlers(settingPluginClazz, value, pluginFor, strValue, true);
    }

    @Override
    public <T> void setValueRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        setValueRaw(settingPluginClazz, value, true);
    }

    public <T> void setValueRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value, boolean fireNotificationOnUI) {
        SettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        T parsedValue = (T) pluginFor.parseValue(entityContext, value);
        String strValue = setValueSilence(pluginFor, parsedValue);
        fireNotifyHandlers(settingPluginClazz, parsedValue, pluginFor, strValue, fireNotificationOnUI);
    }

    private <T> String setValueSilence(SettingPlugin pluginFor, @NotNull T value) {
        String strValue = pluginFor.writeValue(value);
        this.setValueSilenceRaw(pluginFor, strValue);
        return strValue;
    }

    private void setValueSilenceRaw(SettingPlugin<?> pluginFor, @NotNull String value) {
        log.debug("Update setting <{}> value <{}>", SettingEntity.getKey(pluginFor), value);

        if (!pluginFor.isStorable()) {
            return;
        }

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

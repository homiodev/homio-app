package org.homio.app.manager.common.impl;

import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;
import static org.homio.app.manager.common.impl.EntityContextUIImpl.GlobalSendType.setting;
import static org.homio.app.model.entity.SettingEntity.getKey;
import static org.homio.app.repository.SettingRepository.fulfillEntityFromPlugin;

import com.pivovarit.function.ThrowingConsumer;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContextSetting;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.util.CommonUtils;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.repository.SettingRepository;
import org.homio.app.setting.system.SystemPlaceSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;

@Log4j2
@RequiredArgsConstructor
public class EntityContextSettingImpl implements EntityContextSetting {

    @Getter
    private static Path propertiesLocation;
    private static Properties homioProperties;

    public static final Map<String, SettingPlugin> settingPluginsByPluginKey = new HashMap<>();
    public static final Map<Class<? extends DynamicConsoleHeaderContainerSettingPlugin>, List<SettingEntity>> dynamicHeaderSettings = new HashMap<>();
    private static final Map<SettingPlugin, String> settingTransientState = new HashMap<>();
    private static final Map<String, Function<String, String>> settingValuePostProcessors = new HashMap<>();
    private static final Map<String, SettingPlugin> settingPluginsByPluginClass = new HashMap<>();
    private final Map<String, Map<String, ThrowingConsumer<?, Exception>>> settingListeners = new HashMap<>();
    private final Map<String, Map<String, ThrowingConsumer<?, Exception>>> httpRequestSettingListeners = new HashMap<>();
    private final EntityContextImpl entityContext;
    private final ConfigurableEnvironment environment;
    private final ClassFinder classFinder;

    public static List<SettingPlugin> settingPluginsBy(Predicate<SettingPlugin> predicate) {
        return settingPluginsByPluginKey.values().stream().filter(predicate).collect(Collectors.toList());
    }

    @Override
    public List<String> getPlaces() {
        return new ArrayList<>(this.getValue(SystemPlaceSetting.class));
    }

    @Override
    public void reloadSettings(@NotNull Class<? extends SettingPluginOptions> settingPlugin) {
        String entityID = SettingEntity.getKey(settingPlugin);
        SettingPluginOptions<?> pluginOptions = (SettingPluginOptions<?>) EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);

        Collection<OptionModel> options = SettingRepository.getOptions(pluginOptions, entityContext, null);
        entityContext.ui().sendGlobal(setting, entityID, options, null, OBJECT_MAPPER.createObjectNode().put("subType", "list"));
    }

    /**
     * Reload updated settings to UI
     */
    @Override
    public void reloadSettings(@NotNull Class<? extends DynamicConsoleHeaderContainerSettingPlugin> dynamicSettingPluginClass,
        List<? extends DynamicConsoleHeaderSettingPlugin> dynamicSettings) {
        List<SettingEntity> dynamicEntities = dynamicSettings
            .stream()
            .map(this::createSettingEntityFromPlugin)
            .collect(Collectors.toList());
        dynamicHeaderSettings.put(dynamicSettingPluginClass, dynamicEntities);

        entityContext.ui().sendGlobal(setting, SettingEntity.PREFIX + dynamicSettingPluginClass.getSimpleName(),
            dynamicEntities, null, OBJECT_MAPPER.createObjectNode().put("subType", "dynamic"));
    }

    public Object getObjectValue(Class<?> settingPluginClazz) {
        SettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value;
        if (pluginFor.transientState()) {
            value = settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
            value = settingEntity == null ? null : settingEntity.getValue();
        }
        return parseSettingValue(pluginFor, value);
    }

    /**
     * This is post processor that convert raw/saved/default values by using string<->string converter
     */
    public <T> void setSettingRawConverterValue(Class<? extends SettingPlugin<T>> settingPluginClazz, Function<String, String> handler) {
        settingValuePostProcessors.put(settingPluginClazz.getName(), handler);
    }

    @Override
    public <T> T getValue(Class<? extends SettingPlugin<T>> settingPluginClazz) {
        SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String value = this.getRawValue(settingPluginClazz);
        return parseSettingValue(pluginFor, value);
    }

    @Override
    public <T> String getRawValue(Class<? extends SettingPlugin<T>> settingPluginClazz) {
        String key = settingPluginClazz.getName();
        SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(key);
        String value;
        if (pluginFor.transientState()) {
            value = settingTransientState.get(pluginFor);
        } else {
            SettingEntity settingEntity = entityContext.getEntity(SettingEntity.getKey(pluginFor));
            value = settingEntity == null ? null : settingEntity.getValue();
        }
        if (settingValuePostProcessors.containsKey(key)) {
            value = settingValuePostProcessors.get(key).apply(value);
        }

        return value;
    }

    @Override
    public <T> void listenValueInRequest(Class<? extends SettingPlugin<T>> settingClass, @NotNull String key,
        @NotNull ThrowingConsumer<T, Exception> listener) {
        httpRequestSettingListeners.putIfAbsent(settingClass.getName(), new HashMap<>());
        httpRequestSettingListeners.get(settingClass.getName()).put(key, listener);
    }

    @Override
    public <T> void listenValue(Class<? extends SettingPlugin<T>> settingClass, @NotNull String key, @NotNull ThrowingConsumer<T, Exception> listener) {
        settingListeners.putIfAbsent(settingClass.getName(), new HashMap<>());
        settingListeners.get(settingClass.getName()).put(key, listener);
    }

    @Override
    public <T> void unListenValue(Class<? extends SettingPlugin<T>> settingClass, @NotNull String key) {
        if (settingListeners.containsKey(settingClass.getName())) {
            settingListeners.get(settingClass.getName()).remove(key);
        }
        if (httpRequestSettingListeners.containsKey(settingClass.getName())) {
            httpRequestSettingListeners.get(settingClass.getName()).remove(key);
        }
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

    @Override
    @SneakyThrows
    public void setEnv(@NotNull String key, @NotNull Object value) {
        Properties properties = getHomioProperties();
        properties.setProperty(key, value.toString());
        properties.store(Files.newOutputStream(propertiesLocation), null);
    }

    @Override
    public <T> T getEnv(@NotNull String key, @NotNull Class<T> classType, @Nullable T defaultValue, boolean store) {
        String value = getHomioProperties().getProperty(key);
        if (value == null) {
            if (environment.containsProperty(key)) {
                return environment.getProperty(key, classType);
            }
            if (store && defaultValue != null) {
                setEnv(key, defaultValue.toString());
            }
            return defaultValue;
        }
        return environment.getConversionService().convert(value, classType);
    }

    @SneakyThrows
    public static Properties getHomioProperties() {
        if (homioProperties == null) {
            homioProperties = new Properties();
            propertiesLocation = CommonUtils.getHomioPropertiesLocation();
            log.info("Uses configuration file: {}", propertiesLocation);
            // must exist because CommonUtils.logsPath, etc.. init it first
            homioProperties.load(Files.newInputStream(propertiesLocation));
        }
        return homioProperties;
    }

    @Override
    public @NotNull String getApplicationVersion() {
        return StringUtils.defaultIfEmpty(getClass().getPackage().getImplementationVersion(), "0.0");
    }

    @SneakyThrows
    public void updatePlugins(Class<? extends SettingPlugin> settingPluginClass, boolean addAddon) {
        if (Modifier.isPublic(settingPluginClass.getModifiers())) {
            SettingPlugin settingPlugin = settingPluginClass.newInstance();
            String key = SettingEntity.getKey(settingPlugin);
            if (addAddon) {
                settingPluginsByPluginKey.put(key, settingPlugin);
                settingPluginsByPluginClass.put(settingPluginClass.getName(), settingPlugin);
            } else {
                settingPluginsByPluginKey.remove(key);
                settingPluginsByPluginClass.remove(settingPluginClass.getName());
                settingListeners.remove(settingPluginClass.getName());
                httpRequestSettingListeners.remove(settingPluginClass.getName());
            }
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

    @Override
    public <T> void setValue(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull T value) {
        SettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        String strValue = setValueSilence(settingPluginClazz, value);
        fireNotifyHandlers(settingPluginClazz, value, pluginFor, strValue, true);
    }

    @Override
    public <T> void setValueRaw(@NotNull Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value) {
        setValueRaw(settingPluginClazz, value, true);
    }

    public <T> void setValueRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value, boolean fireNotificationOnUI) {
        SettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
        T parsedValue = (T) pluginFor.parseValue(entityContext, value);
        String strValue = setValueSilence(pluginFor, parsedValue);
        fireNotifyHandlers(settingPluginClazz, parsedValue, pluginFor, strValue, fireNotificationOnUI);
    }

    @SneakyThrows
    public void onContextCreated() {
        List<Class<? extends SettingPlugin>> settingClasses = classFinder.getClassesWithParent(SettingPlugin.class);
        addSettingsFromSystem(settingClasses);
    }

    public void addSettingsFromClassLoader(AddonContext addonContext) {
        List<Class<? extends SettingPlugin>> settingClasses = classFinder.getClassesWithParent(SettingPlugin.class, null, addonContext.getClassLoader());
        addSettingsFromSystem(settingClasses);
        addonContext.onDestroy(() -> {
            for (Class<? extends SettingPlugin> settingPluginClass : settingClasses) {
                SettingPlugin settingPlugin = CommonUtils.newInstance(settingPluginClass);
                String key = SettingEntity.getKey(settingPlugin);
                settingPluginsByPluginKey.remove(key);
                settingPluginsByPluginClass.remove(settingPluginClass.getName());
                settingListeners.remove(settingPluginClass.getName());
                httpRequestSettingListeners.remove(settingPluginClass.getName());
            }
        });
    }

    private void addSettingsFromSystem(List<Class<? extends SettingPlugin>> settingClasses) {
        for (Class<? extends SettingPlugin> settingPluginClass : settingClasses) {
            if (Modifier.isPublic(settingPluginClass.getModifiers())) {
                SettingPlugin settingPlugin = CommonUtils.newInstance(settingPluginClass);
                String key = SettingEntity.getKey(settingPlugin);
                settingPluginsByPluginKey.put(key, settingPlugin);
                settingPluginsByPluginClass.put(settingPluginClass.getName(), settingPlugin);
            }
        }
    }

    private SettingEntity createSettingEntityFromPlugin(SettingPlugin<?> settingPlugin) {
        SettingEntity settingEntity = new SettingEntity().setEntityID(getKey(settingPlugin));
        fulfillEntityFromPlugin(settingEntity, entityContext, settingPlugin);
        return settingEntity;
    }

    @Nullable
    private <T> T parseSettingValue(SettingPlugin<T> pluginFor, String value) {
        try {
            return pluginFor.parseValue(entityContext, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
        } catch (Exception ex) {
            log.error("Unable to parse value: '{}' to type: '{}'", value, pluginFor.getType());
            return null;
        }
    }

    private <T> void fireNotifyHandlers(Class<? extends SettingPlugin<T>> settingPluginClazz, T value,
        SettingPlugin pluginFor, String strValue, boolean fireUpdatesToUI) {
        if (settingListeners.containsKey(settingPluginClazz.getName())) {
            entityContext.bgp().builder("update-setting-" + settingPluginClazz.getSimpleName()).auth().execute(() -> {
                for (ThrowingConsumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
                    try {
                        consumer.accept(value);
                    } catch (Exception ex) {
                        entityContext.ui().sendErrorMessage(ex);
                        log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value, ex);
                    }
                }
            });
        }
        Map<String, ThrowingConsumer<?, Exception>> requestListeners = httpRequestSettingListeners.get(settingPluginClazz.getName());
        if (requestListeners != null) {
            for (ThrowingConsumer consumer : requestListeners.values()) {
                try {
                    consumer.accept(value);
                } catch (Exception ex) {
                    entityContext.ui().sendErrorMessage(ex);
                    log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value, ex);
                }
            }
        }

        entityContext.event().fireEvent(SettingEntity.getKey(settingPluginClazz), StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue()));

        if (fireUpdatesToUI) {
            entityContext.ui().sendGlobal(setting, SettingEntity.getKey(settingPluginClazz), value, null,
                OBJECT_MAPPER.createObjectNode().put("subType", "single"));
        }
    }

    private <T> String setValueSilence(SettingPlugin pluginFor, @Nullable T value) {
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
                if (Objects.equals(settingEntity.getDefaultValue(), value)) {
                    value = "";
                }
                entityContext.save(settingEntity.setValue(value));
            }
        }
    }
}

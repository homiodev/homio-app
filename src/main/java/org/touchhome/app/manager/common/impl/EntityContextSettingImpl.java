package org.touchhome.app.manager.common.impl;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.setting.system.SystemPlaceSetting;
import org.touchhome.bundle.api.EntityContextSetting;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginOptions;
import org.touchhome.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.touchhome.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;

@Log4j2
@RequiredArgsConstructor
public class EntityContextSettingImpl implements EntityContextSetting {

  private static final Map<SettingPlugin, String> settingTransientState = new HashMap<>();
  public static final Map<String, SettingPlugin> settingPluginsByPluginKey = new HashMap<>();
  public static final Map<Class<? extends DynamicConsoleHeaderContainerSettingPlugin>, List<SettingEntity>>
      dynamicHeaderSettings = new HashMap<>();
  private static final Map<String, SettingPlugin> settingPluginsByPluginClass = new HashMap<>();
  private final Map<String, Map<String, Consumer<?>>> settingListeners = new HashMap<>();
  private final EntityContextImpl entityContext;

  public static List<SettingPlugin> settingPluginsBy(Predicate<SettingPlugin> predicate) {
    return settingPluginsByPluginKey.values().stream().filter(predicate).collect(Collectors.toList());
  }

  @Override
  public List<String> getPlaces() {
    return new ArrayList<>(this.getValue(SystemPlaceSetting.class));
  }

  @Override
  public void reloadSettings(Class<? extends SettingPluginOptions> settingPlugin) {
    String entityID = SettingEntity.getKey(settingPlugin);
    SettingPluginOptions<?> pluginOptions =
        (SettingPluginOptions<?>) EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);

    Collection<OptionModel> options = SettingRepository.getOptions(pluginOptions, entityContext, null);
    entityContext.ui().sendGlobal(EntityContextUIImpl.GlobalSendType.setting, entityID, options, null,
        new JSONObject().put("subType", "list"));
  }

  @Override
  public void reloadSettings(Class<? extends DynamicConsoleHeaderContainerSettingPlugin> dynamicSettingPluginClass,
      List<? extends DynamicConsoleHeaderSettingPlugin> dynamicSettings) {
    List<SettingEntity> dynamicEntities = dynamicSettings.stream()
        .map(s -> SettingRepository.createSettingEntityFromPlugin(s, new SettingEntity(), entityContext))
        .collect(Collectors.toList());
    dynamicHeaderSettings.put(dynamicSettingPluginClass, dynamicEntities);

    entityContext.ui().sendGlobal(EntityContextUIImpl.GlobalSendType.setting,
        SettingEntity.PREFIX + dynamicSettingPluginClass.getSimpleName(),
        dynamicEntities, null, new JSONObject().put("subType", "dynamic"));
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

  @Nullable
  private <T> T parseSettingValue(SettingPlugin<T> pluginFor, String value) {
    try {
      return pluginFor.parseValue(entityContext, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    } catch (Exception ex) {
      log.error("Unable to parse value: '{}' to type: '{}'", value, pluginFor.getType());
      return null;
    }
  }

  @Override
  public <T> T getValue(Class<? extends SettingPlugin<T>> settingPluginClazz) {
    SettingPlugin<T> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
    String value = this.getRawValue(settingPluginClazz);
    return parseSettingValue(pluginFor, value);
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
        this.entityContext.bgp().builder(key).execute(() -> listener.accept(value)));
  }

  @Override
  public <T> void listenValue(Class<? extends SettingPlugin<T>> settingClass, String key, Consumer<T> listener) {
    settingListeners.putIfAbsent(settingClass.getName(), new HashMap<>());
    settingListeners.get(settingClass.getName()).put(key, listener);
  }

  public void listenObjectValue(Class<?> settingClass, String key, Consumer<Object> listener) {
    settingListeners.putIfAbsent(settingClass.getName(), new HashMap<>());
    settingListeners.get(settingClass.getName()).put(key, listener);
  }

  @Override
  public <T> void unListenValue(Class<? extends SettingPlugin<T>> settingClass, String key) {
    if (settingListeners.containsKey(settingClass.getName())) {
      settingListeners.get(settingClass.getName()).remove(key);
    }
  }

  public void unListenWithPrefix(String prefix) {
    for (Map<String, Consumer<?>> entryMap : settingListeners.values()) {
      entryMap.keySet().removeIf(s -> s.startsWith(prefix));
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

  private <T> void fireNotifyHandlers(Class<? extends SettingPlugin<T>> settingPluginClazz, T value,
      SettingPlugin pluginFor, String strValue, boolean fireUpdatesToUI) {
    if (settingListeners.containsKey(settingPluginClazz.getName())) {
      entityContext.bgp().builder("update-setting-" + settingPluginClazz.getSimpleName()).execute(() -> {
        for (Consumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
          try {
            consumer.accept(value);
          } catch (Exception ex) {
            entityContext.ui().sendErrorMessage(ex);
            log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(),
                value, ex);
          }
        }
      });
    }
    entityContext.event().fireEvent(SettingEntity.getKey(settingPluginClazz),
        StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue()));

    if (fireUpdatesToUI) {
      entityContext.ui().sendGlobal(EntityContextUIImpl.GlobalSendType.setting, SettingEntity.getKey(settingPluginClazz),
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

  public <T> void setValueRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @NotNull String value,
      boolean fireNotificationOnUI) {
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
        if (Objects.equals(settingEntity.getDefaultValue(), value)) {
          value = "";
        }
        entityContext.save(settingEntity.setValue(value));
      }
    }
  }

  public void fetchSettingPlugins(String basePackage, ClassFinder classFinder, boolean addBundle) {
    List<Class<? extends SettingPlugin>> classes = classFinder.getClassesWithParent(SettingPlugin.class, null, basePackage);
    for (Class<? extends SettingPlugin> settingPlugin : classes) {
      updatePlugins(settingPlugin, addBundle);
    }
  }
}

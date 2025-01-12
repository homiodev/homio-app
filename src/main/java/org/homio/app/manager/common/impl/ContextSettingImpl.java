package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.ContextSetting;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.common.ClassFinder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.repository.SettingRepository;
import org.homio.app.setting.system.SystemPlaceSetting;
import org.homio.app.setting.system.proxy.SystemProxyAddressSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.manager.common.impl.ContextUIImpl.GlobalSendType.setting;
import static org.homio.app.model.entity.SettingEntity.getKey;
import static org.homio.app.repository.SettingRepository.fulfillEntityFromPlugin;

@SuppressWarnings("unused")
@Log4j2
@RequiredArgsConstructor
public class ContextSettingImpl implements ContextSetting {

  public static final Map<String, SettingPlugin> settingPluginsByPluginKey = new HashMap<>();
  public static final Map<Class<? extends DynamicConsoleHeaderContainerSettingPlugin>, List<SettingEntity>> dynamicHeaderSettings = new HashMap<>();
  public static final Map<String, UserSettings> settings = new HashMap<>();
  private static final Map<SettingPlugin, String> settingTransientState = new HashMap<>();
  private static final Map<String, Function<String, String>> settingValuePostProcessors = new HashMap<>();
  @Getter
  private static Path propertiesLocation;
  private static CommentedProperties homioProperties;
  private final Map<String, SettingPlugin> settingPluginsByPluginClass = new HashMap<>();
  private final Map<String, Map<String, ThrowingConsumer<?, Exception>>> settingListeners = new HashMap<>();
  private final Map<String, Map<String, ThrowingConsumer<?, Exception>>> httpRequestSettingListeners = new HashMap<>();
  private final ContextImpl context;
  private final ConfigurableEnvironment environment;
  private final ClassFinder classFinder;

  public static List<SettingPlugin> settingPluginsBy(Predicate<SettingPlugin> predicate) {
    return settingPluginsByPluginKey.values().stream().filter(predicate).collect(Collectors.toList());
  }

  @SneakyThrows
  public static CommentedProperties getHomioProperties() {
    if (homioProperties == null) {
      homioProperties = new CommentedProperties();
      propertiesLocation = CommonUtils.getRootPath().resolve("homio.properties");
      log.info("Uses configuration file: {}", propertiesLocation);
      try {
        homioProperties.load(Files.newInputStream(propertiesLocation));
      } catch (Exception ignore) {
        homioProperties.store(Files.newOutputStream(propertiesLocation), null);
      }
    }
    return homioProperties;
  }

  private static Map<SettingPlugin, String> getTransientState(@Nullable UserEntity user) {
    if (user != null && !user.isAdmin()) {
      return settings.computeIfAbsent(user.getEntityID(), s -> new UserSettings()).settingTransientState;
    }
    return settingTransientState;
  }

  private static void setSystemProxy(String ip, String port) {
    System.setProperty("java.net.useSystemProxies", StringUtils.isEmpty(ip) ? "false" : "true");
    System.setProperty("http.proxyHost", ip);
    System.setProperty("https.proxyHost", ip);
    System.setProperty("http.proxyPort", port);
    System.setProperty("https.proxyPort", port);
  }

  @Override
  public List<String> getPlaces() {
    return new ArrayList<>(this.getValue(SystemPlaceSetting.class));
  }

  @Override
  public void reloadSettings(@NotNull Class<? extends SettingPluginOptions> settingPlugin) {
    String entityID = SettingEntity.getKey(settingPlugin);
    SettingPluginOptions<?> pluginOptions = (SettingPluginOptions<?>) ContextSettingImpl.settingPluginsByPluginKey.get(entityID);

    Collection<OptionModel> options = SettingRepository.getOptions(pluginOptions, context, null);
    context.ui().sendGlobal(setting, entityID, options, null, OBJECT_MAPPER.createObjectNode().put("subType", "list"));
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

    context.ui().sendGlobal(setting, SettingEntity.PREFIX + dynamicSettingPluginClass.getSimpleName(),
      dynamicEntities, null, OBJECT_MAPPER.createObjectNode().put("subType", "dynamic"));
  }

  public Object getObjectValue(Class<?> settingPluginClazz) {
    SettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
    String value;
    if (pluginFor.transientState()) {
      value = settingTransientState.get(pluginFor);
    } else {
      SettingEntity settingEntity = context.db().get(SettingEntity.getKey(pluginFor));
      value = settingEntity == null ? null : settingEntity.getValue();
    }
    return parseSettingValue(pluginFor, value);
  }

  /**
   * This is post processor that convert raw/saved/default values by using string - string converter
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
      SettingEntity settingEntity = context.db().get(SettingEntity.getKey(pluginFor));
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
  public <T extends BaseEntity & HasJsonData, P> void listenEntityValueAndGet(@NotNull T entity, @NotNull String key, @Nullable String jsonKey,
                                                                              @NotNull Class<P> typeClass, @NotNull ThrowingConsumer<P, Exception> listener) {
    throw new NotImplementedException("Not implemented yet");
  }

  @Override
  public <T> String setValueSilence(Class<? extends SettingPlugin<T>> settingPluginClazz, @Nullable T value) {
    SettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
    String strValue = pluginFor.serializeValue(value);
    this.setValueSilenceRaw(pluginFor, strValue);
    return strValue;
  }

  @Override
  public <T> void setValueSilenceRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @Nullable String value) {
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
  public <T> T getEnv(@NotNull String key, @NotNull Class<T> classType, @Nullable T defaultValue, boolean store,
                      @Nullable String description) {
    String value = getHomioProperties().getProperty(key);
    if (value == null) {
      if (environment.containsProperty(key)) {
        return environment.getProperty(key, classType);
      }
      if (store && defaultValue != null) {
        if (StringUtils.isNotEmpty(description)) {
          getHomioProperties().addComment(key, description);
        }
        setEnv(key, defaultValue.toString());
      }
      return defaultValue;
    }
    return environment.getConversionService().convert(value, classType);
  }

  @Override
  public @NotNull String getApplicationVersion() {
    return System.getProperty("server.version");
  }

  @SneakyThrows
  public void updatePlugins(Class<? extends SettingPlugin<?>> settingPluginClass, boolean addAddon) {
    if (Modifier.isPublic(settingPluginClass.getModifiers())) {
      SettingPlugin<?> settingPlugin = CommonUtils.newInstance(settingPluginClass);
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
    SettingEntity settingEntity = context.db().getRequire(SettingEntity.getKey(pluginFor));
    T value = pluginFor.deserializeValue(context, settingEntity.getValue());
    fireNotifyHandlers(settingPluginClazz, value, pluginFor, settingEntity.getValue(), true);
  }

  @Override
  public <T> void setValue(Class<? extends SettingPlugin<T>> settingPluginClazz, @Nullable T value) {
    SettingPlugin pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
    String strValue = setValueSilence(settingPluginClazz, value);
    fireNotifyHandlers(settingPluginClazz, value, pluginFor, strValue, true);
  }

  @Override
  public <T> void setValueRaw(@NotNull Class<? extends SettingPlugin<T>> settingPluginClazz, @Nullable String value) {
    setValueRaw(settingPluginClazz, value, true);
  }

  public <T> void setValueRaw(Class<? extends SettingPlugin<T>> settingPluginClazz, @Nullable String value, boolean fireNotificationOnUI) {
    SettingPlugin<?> pluginFor = settingPluginsByPluginClass.get(settingPluginClazz.getName());
    T parsedValue = (T) pluginFor.deserializeValue(context, value);
    String strValue = setValueSilence(pluginFor, parsedValue);
    fireNotifyHandlers(settingPluginClazz, parsedValue, pluginFor, strValue, fireNotificationOnUI);
  }

  @SneakyThrows
  public void onContextCreated() {
    List<Class<? extends SettingPlugin>> settingClasses = classFinder.getClassesWithParent(SettingPlugin.class);
    addSettingsFromSystem(settingClasses);
    configureProxy();
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
    SettingEntity entity = new SettingEntity();
    entity.setEntityID(getKey(settingPlugin));
    fulfillEntityFromPlugin(entity, context, settingPlugin);
    return entity;
  }

  @Nullable
  private <T> T parseSettingValue(SettingPlugin<T> pluginFor, String value) {
    try {
      return pluginFor.deserializeValue(context, StringUtils.defaultIfEmpty(value, pluginFor.getDefaultValue()));
    } catch (Exception ex) {
      log.error("Unable to parse value: '{}' to type: '{}'", value, pluginFor.getType());
      return null;
    }
  }

  private <T> void fireNotifyHandlers(Class<? extends SettingPlugin<T>> settingPluginClazz, T value,
                                      SettingPlugin pluginFor, String strValue, boolean fireUpdatesToUI) {
    if (settingListeners.containsKey(settingPluginClazz.getName())) {
      context.bgp().builder("update-setting-" + settingPluginClazz.getSimpleName()).auth().execute(() -> {
        for (ThrowingConsumer consumer : settingListeners.get(settingPluginClazz.getName()).values()) {
          try {
            consumer.accept(value);
          } catch (Exception ex) {
            context.ui().toastr().error(ex);
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
          context.ui().toastr().error(ex);
          log.error("Error while fire listener for setting <{}>. Value: <{}>", settingPluginClazz.getSimpleName(), value, ex);
        }
      }
    }

    context.event().fireEvent(SettingEntity.getKey(settingPluginClazz),
      new StringType(StringUtils.defaultIfEmpty(strValue, pluginFor.getDefaultValue())));

    if (fireUpdatesToUI) {
      context.ui().sendGlobal(setting, SettingEntity.getKey(settingPluginClazz), value, null,
        OBJECT_MAPPER.createObjectNode().put("subType", "single"));
    }
  }

  private <T> String setValueSilence(SettingPlugin pluginFor, @Nullable T value) {
    String strValue = pluginFor.serializeValue(value);
    this.setValueSilenceRaw(pluginFor, strValue);
    return strValue;
  }

  @SneakyThrows
  private void setValueSilenceRaw(SettingPlugin<?> pluginFor, @Nullable String value) {
    log.debug("Update setting <{}> value <{}>", SettingEntity.getKey(pluginFor), value);

    UserEntity user = context.user().getLoggedInUser();
    pluginFor.assertUserAccess(context, user);

    if (!pluginFor.isStorable()) {
      return;
    }

    if (pluginFor.transientState()) {
      getTransientState(user).put(pluginFor, value);
    } else {
      SettingEntity settingEntity = context.db().getRequire(SettingEntity.getKey(pluginFor));
      if (!Objects.equals(value, settingEntity.getValue())) {
        if (Objects.equals(settingEntity.getDefaultValue(), value)) {
          value = "";
        }
        context.db().save(settingEntity.setValue(value));
      }
    }
  }

  @SneakyThrows
  private void configureProxy() {
    listenValueAndGet(SystemProxyAddressSetting.class, "proxy-host", proxyUrl -> {
      if (StringUtils.isEmpty(proxyUrl)) {
        setSystemProxy("", "");
        return;
      }
      String[] items = proxyUrl.split(":");
      if (items.length != 2) {
        throw new IllegalArgumentException("Proxy address must be in format: ip:port");
      }
      setSystemProxy(items[0], items[1]);
    });
  }

  public static class CommentedProperties extends Properties {

    private final Map<String, String> comments = new LinkedHashMap<>();

    public void addComment(String key, String comment) {
      comments.put(key, comment);
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));
      String line;
      StringBuilder buffer = new StringBuilder();
      String currentKey = null;

      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (line.startsWith("#") || line.startsWith("!")) {
          buffer.append(line);
          buffer.append(System.lineSeparator());
        } else if (line.contains("=")) {
          if (currentKey != null) {
            comments.put(currentKey, buffer.toString());
            buffer = new StringBuilder();
          }

          int index = line.indexOf('=');
          String key = line.substring(0, index).trim();
          String value = line.substring(index + 1).trim();
          currentKey = key;
          super.setProperty(key, value);
        }
      }

      if (currentKey != null) {
        comments.put(currentKey, buffer.toString());
      }
    }

    @Override
    public synchronized void store(Writer writer, String comments) throws IOException {
      BufferedWriter bw = new BufferedWriter(writer);
      if (comments != null) {
        bw.write("# ");
        bw.write(comments);
        bw.write(System.lineSeparator());
      }

      for (Map.Entry<Object, Object> entry : entrySet()) {
        String key = (String) entry.getKey();
        String value = (String) entry.getValue();

        if (comments != null && this.comments.containsKey(key)) {
          bw.write("# ");
          bw.write(this.comments.get(key));
          bw.write(System.lineSeparator());
        }

        bw.write(key + "=" + value);
        bw.write(System.lineSeparator());
      }
      bw.flush();
    }
  }

  public static class UserSettings {
    private final Map<SettingPlugin, String> settingTransientState = new HashMap<>();
  }
}

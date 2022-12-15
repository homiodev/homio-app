package org.touchhome.app.manager.common.impl;

import static java.util.Collections.emptyMap;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP.ScheduleBuilder;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.EntityContextEvent;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.BaseEntityIdentifier;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.common.util.Lang;

@Log4j2
public class EntityContextEventImpl implements EntityContextEvent {

  @Getter
  private final EntityListener entityUpdateListeners = new EntityListener();

  @Getter
  private final EntityListener entityCreateListeners = new EntityListener();

  @Getter
  private final EntityListener entityRemoveListeners = new EntityListener();

  @Getter
  private final Set<OptionModel> events = new HashSet<>();
  private final Map<String, Object> lastValues = new ConcurrentHashMap<>();

  private final Map<String, Map<String, Consumer<Object>>> eventListeners = new ConcurrentHashMap<>();

  @Getter
  private final List<BiConsumer<String, Object>> globalEvenListeners = new ArrayList<>();
  private final ScheduleBuilder<Boolean> internetAccessBuilder;
  // constructor parameters
  private final EntityContextImpl entityContext;
  private final TouchHomeProperties touchHomeProperties;
  private ThreadContext<Boolean> internetThreadContext;

  public EntityContextEventImpl(EntityContextImpl entityContext, TouchHomeProperties touchHomeProperties) {
    this.entityContext = entityContext;
    this.touchHomeProperties = touchHomeProperties;

    ScheduleBuilder<Boolean> builder = this.entityContext.bgp().builder("internet-test");
    Duration interval = touchHomeProperties.getInternetTestInterval();
    this.internetAccessBuilder = builder.interval(interval).delay(interval).interval(interval)
                                        .tap(context -> this.internetThreadContext = context);
  }

  public void onContextCreated() {
    listenInternetStatus();
  }

  @Override
  public void removeEvents(String key, String... additionalKeys) {
    eventListeners.remove(key);
    lastValues.remove(key);
    for (String additionalKey : additionalKeys) {
      eventListeners.remove(additionalKey);
      lastValues.remove(additionalKey);
    }
  }

  @Override
  public EntityContextEvent addEventBehaviourListener(String key, String discriminator, Consumer<Object> listener) {
    if (lastValues.containsKey(key)) {
      listener.accept(lastValues.get(key));
    }
    addEventListener(key, discriminator, listener);
    return this;
  }

  @Override
  public EntityContextEvent addEventListener(String key, String discriminator, Consumer<Object> listener) {
    eventListeners.computeIfAbsent(discriminator, d -> new ConcurrentHashMap<>())
                  .put(key, listener);
    return this;
  }

  @Override
  public EntityContextEvent fireEventIfNotSame(@NotNull String key, @Nullable Object value) {
    return fireEvent(key, value, true);
  }

  @Override
  public EntityContextEvent fireEvent(@NotNull String key, @Nullable Object value) {
    return fireEvent(key, value, false);
  }

  @NotNull
  private EntityContextEventImpl fireEvent(@NotNull String key, @Nullable Object value, boolean compareValues) {
    // fire by key and key + value type
    fireEventInternal(key, value, compareValues);
    if (value != null && !(value instanceof String)) {
      fireEventInternal(key + "_" + value.getClass().getSimpleName(), value, compareValues);
    }
    return this;
  }

  private void fireEventInternal(@NotNull String key, @Nullable Object value, boolean compareValues) {
    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("Unable to fire event with empty key");
    }
    if (value != null) {
      if (compareValues && Objects.equals(value, lastValues.get(key))) {
        return;
      }
      lastValues.put(key, value);
    }

    for (Map<String, Consumer<Object>> eventListenerMap : eventListeners.values()) {
      if (eventListenerMap.containsKey(key)) {
        eventListenerMap.get(key).accept(value);
      }
    }
    globalEvenListeners.forEach(l -> l.accept(key, value));
    entityContext.fireAllBroadcastLock(broadcastLockManager -> broadcastLockManager.signalAll(key, value));
  }

  public void addEvent(String key) {
    OptionModel optionModel = OptionModel.of(key, Lang.getServerMessage(key));
    this.events.add(optionModel);
  }

  @Override
  public EntityContextEvent removeEntityUpdateListener(String entityID, String key) {
    if (this.entityUpdateListeners.idListeners.containsKey(entityID)) {
      this.entityUpdateListeners.idListeners.get(entityID).remove(key);
    }
    if (this.entityUpdateListeners.idBiListeners.containsKey(entityID)) {
      this.entityUpdateListeners.idBiListeners.get(entityID).remove(key);
    }
    return this;
  }

  @Override
  public EntityContextEvent removeEntityRemoveListener(String entityID, String key) {
    if (this.entityRemoveListeners.idListeners.containsKey(entityID)) {
      this.entityRemoveListeners.idListeners.get(entityID).remove(key);
    }
    return this;
  }

  @Override
  public void runOnceOnInternetUp(@NotNull String name, @NotNull ThrowingRunnable<Exception> command) {
    this.internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
      if (isInternetUp) {
        log.info("Internet up. Run <" + name + "> listener.");
        try {
          command.run();
        } catch (Exception ex) {
          log.error("Error occurs while run command: " + name, ex);
        }
        return true;
      }
      return false;
    });
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(String entityID, String key, Consumer<T> listener) {
    this.entityUpdateListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityUpdateListeners.idListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(String entityID, String key,
      EntityContext.EntityUpdateListener<T> listener) {
    this.entityUpdateListeners.idBiListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityUpdateListeners.idBiListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(Class<T> entityClass, String key, Consumer<T> listener) {
    this.entityUpdateListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityUpdateListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(Class<T> entityClass, String key,
      EntityContext.EntityUpdateListener<T> listener) {
    this.entityUpdateListeners.typeBiListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityUpdateListeners.typeBiListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityCreateListener(Class<T> entityClass, String key, Consumer<T> listener) {
    this.entityCreateListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityCreateListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityRemovedListener(String entityID, String key, Consumer<T> listener) {
    this.entityRemoveListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityRemoveListeners.idListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> EntityContextEvent addEntityRemovedListener(Class<T> entityClass, String key,
      Consumer<T> listener) {
    this.entityRemoveListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityRemoveListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  private void listenInternetStatus() {
    internetThreadContext.addValueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
      if (isInternetUp != isInternetWasUp) {
        entityContext.event().fireEventIfNotSame("internet-status", isInternetUp ? Status.ONLINE : Status.OFFLINE);
        if (isInternetUp) {
          entityContext.ui().removeBellNotification("internet-connection");
          entityContext.ui().addBellInfoNotification("internet-connection", "Internet Connection", "Internet up");
        } else {
          entityContext.ui().removeBellNotification("internet-up");
          entityContext.ui().addBellErrorNotification("internet-connection", "Internet Connection", "Internet down");
        }
      }
      return null;
    });
    internetAccessBuilder.execute(context -> entityContext.checkUrlAccessible() != null);
  }

  @Getter
  public static class EntityListener {

    private final Map<String, Map<String, EntityContext.EntityUpdateListener>> typeBiListeners = new HashMap<>();
    private final Map<String, Map<String, Consumer>> typeListeners = new HashMap<>();

    private final Map<String, Map<String, Consumer>> idListeners = new HashMap<>();
    private final Map<String, Map<String, EntityContext.EntityUpdateListener>> idBiListeners = new HashMap<>();

    public <T extends HasEntityIdentifier> void notify(T saved, T oldEntity) {
      // notify by entityID
      String entityId = saved == null ? oldEntity.getEntityID() : saved.getEntityID();
      for (Consumer listener : idListeners.getOrDefault(entityId, emptyMap()).values()) {
        listener.accept(saved);
      }
      for (EntityContext.EntityUpdateListener listener : idBiListeners.getOrDefault(entityId, emptyMap()).values()) {
        listener.entityUpdated(saved, oldEntity);
      }

      // notify by class type
      Class typeClass = saved == null ? oldEntity.getClass() : saved.getClass();

      for (Class<?> entityClass : ClassUtils.getAllInterfaces(typeClass)) {
        this.notifyByType(entityClass.getName(), saved, oldEntity);
      }
      for (Class<?> entityClass : ClassUtils.getAllSuperclasses(typeClass)) {
        this.notifyByType(entityClass.getName(), saved, oldEntity);
      }
      this.notifyByType(typeClass.getName(), saved, oldEntity);
    }

    private <T extends HasEntityIdentifier> void notifyByType(String name, T saved, T oldEntity) {
      for (EntityContext.EntityUpdateListener listener : typeBiListeners.getOrDefault(name, emptyMap())
                                                                        .values()) {
        listener.entityUpdated(saved, oldEntity);
      }
      for (Consumer listener : typeListeners.getOrDefault(name, emptyMap()).values()) {
        listener.accept(saved == null ? oldEntity : saved); // for Delete we have to use oldEntity
      }
    }

    public <T extends BaseEntity> boolean isRequireFetchOldEntity(T entity) {
      if (!idBiListeners.getOrDefault(entity.getEntityID(), emptyMap()).isEmpty()) {
        return true;
      }
      Class<?> cursor = entity.getClass();
      while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
        if (!typeBiListeners.getOrDefault(cursor.getName(), emptyMap()).isEmpty()) {
          return true;
        }
        cursor = cursor.getSuperclass();
      }
      return false;
    }
  }
}

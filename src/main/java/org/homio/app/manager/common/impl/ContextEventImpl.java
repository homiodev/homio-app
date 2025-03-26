package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingRunnable;
import jakarta.persistence.EntityManagerFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.event.internal.PostDeleteEventListenerStandardImpl;
import org.hibernate.event.internal.PostInsertEventListenerStandardImpl;
import org.hibernate.event.internal.PostUpdateEventListenerStandardImpl;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.internal.SessionFactoryImpl;
import org.homio.api.ContextEvent;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.BaseEntityIdentifier;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.OptionModel;
import org.homio.api.service.EntityService;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.ContextImpl.ItemAction;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.service.mem.InMemoryDB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;
import static org.apache.xmlbeans.XmlBeans.getTitle;

@Log4j2
public class ContextEventImpl implements ContextEvent {

  @Getter
  private final EntityListener entityUpdateStatusListeners = new EntityListener();

  @Getter
  private final EntityListener entityUpdateListeners = new EntityListener();

  @Getter
  private final EntityListener entityCreateListeners = new EntityListener();

  @Getter
  private final EntityListener entityRemoveListeners = new EntityListener();

  private final @Getter Set<OptionModel> events = new HashSet<>();
  private final @Getter Map<String, State> lastValues = new ConcurrentHashMap<>();

  private final Map<String, Map<String, Consumer<State>>> eventListeners = new ConcurrentHashMap<>();
  private final Map<String, Map<Pattern, BiConsumer<String, State>>> eventRegexpListeners = new ConcurrentHashMap<>();

  @Getter
  private final List<BiConsumer<String, Object>> globalEvenListeners = new ArrayList<>();

  // constructor parameters
  private final ContextImpl context;

  private final BlockingQueue<EntityUpdate> entityUpdatesQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();

  private final Map<String, EventTTL> eventsTTL = new ConcurrentHashMap<>();

  public ContextEventImpl(ContextImpl context, EntityManagerFactory entityManagerFactory) {
    this.context = context;
    registerEntityListeners(entityManagerFactory);
    context.bgp().addLowPriorityRequest("remove-ttl-events", () -> {
      for (EventTTL event : eventsTTL.values()) {
        if (System.currentTimeMillis() > (event.created + event.ttl.toMillis())) {
          removeEventListener(event.discriminator, event.key);
        }
      }
    });
  }

  private static void postInsertUpdate(ContextImpl context, Object entity, boolean persist) {
    if (entity instanceof BaseEntity baseEntity) {
      baseEntity.setContext(context);
      loadEntityService(context, entity);
      if (persist) {
        baseEntity.afterPersist();
      } else {
        baseEntity.afterUpdate();
      }
      // corner case if save/update WorkspaceVariable
      if (entity instanceof WorkspaceVariable wv) {
        entity = wv.getTopGroup();
      } else if (entity instanceof WorkspaceGroup wg && wg.getParent() != null) {
        persist = false;
        entity = context.db().get(wg.getParent());
      } else if (entity instanceof WidgetEntity<?> we) {
        we.afterUpdate();
      }
      context.sendEntityUpdateNotification(entity, persist ? ItemAction.Insert : ItemAction.Update);
    }
  }

  // Try to instantiate service associated with entity
  private static void loadEntityService(ContextImpl context, Object entity) {
    if (entity instanceof EntityService es) {
      try {
        Optional<?> serviceOptional = ((EntityService<?>) entity).getOrCreateService(context);
        // Update entity into service
        if (serviceOptional.isPresent()) {
          try {
            var service = (ServiceInstance) serviceOptional.get();
            service.entityUpdated((EntityService) entity);
          } catch (Exception ex) {
            ((EntityService<?>) entity).setStatusError(ex);
          }
        }
      } catch (Exception ex) {
        log.error("[{}]: Unable to create EntityService for entity: {}.Msg: {}", es.getEntityID(), es, CommonUtils.getErrorMessage(ex));
      }
    }
  }

  /**
   * Require to run inside transaction! to load lazy loaded groups/parents/etc...
   */
  private static void updateCacheEntity(ContextImpl context, Object entity, ItemAction type) {
    try {
      if (entity instanceof BaseEntity) {
        context.getCacheService().entityUpdated((BaseEntity) entity);
      }
    } catch (Exception ex) {
      log.error("Unable to update cache entity <{}> for entity: <{}>. Msg: <{}>", type, entity, CommonUtils.getErrorMessage(ex));
    }
  }

  @Override
  public synchronized void removeEvents(@NotNull String discriminator, String... additionalKeys) {
    removeEventListener(discriminator);
    for (String additionalKey : additionalKeys) {
      removeEventListener(additionalKey);
    }
  }

  private void removeEventListener(String discriminator) {
    Map<String, Consumer<State>> removedEvents = eventListeners.remove(discriminator);
    lastValues.remove(discriminator);
    if (removedEvents != null) {
      for (String key : removedEvents.keySet()) {
        eventsTTL.remove(discriminator + key);
      }
    }
  }

  @Override
  public synchronized void removeEventListener(@Nullable String discriminator, @NotNull String key) {
    discriminator = discriminator == null ? "" : discriminator;
    Map<String, Consumer<State>> map = eventListeners.get(discriminator);
    eventsTTL.remove(discriminator + key);
    if (map != null) {
      map.remove(key);
      if (map.isEmpty()) {
        eventListeners.remove(discriminator);
      }
    }
  }

  @Override
  public @NotNull ContextEvent addEventBehaviourListener(@NotNull String key, @Nullable String discriminator,
                                                         @Nullable Duration ttl,
                                                         @NotNull Consumer<State> listener) {
    if (lastValues.containsKey(key)) {
      listener.accept(lastValues.get(key));
    }
    addEventListener(key, discriminator, ttl, listener);
    return this;
  }

  @Override
  public @NotNull ContextEvent addEventListener(@NotNull String key, @Nullable String discriminator, @Nullable Duration ttl, @NotNull Consumer<State> listener) {
    discriminator = discriminator == null ? "" : discriminator;
    if (ttl != null) {
      eventsTTL.put(discriminator + key, new EventTTL(key, discriminator, ttl, System.currentTimeMillis()));
    }
    eventListeners.computeIfAbsent(discriminator, d -> new ConcurrentHashMap<>()).put(key, listener);
    return this;
  }

  @Override
  public @NotNull ContextEvent addEventBehaviourListener(@NotNull Pattern regexp, @Nullable String discriminator, @NotNull BiConsumer<String, State> listener) {
    for (Entry<String, State> entry : lastValues.entrySet()) {
      if (regexp.matcher(entry.getKey()).matches()) {
        listener.accept(entry.getKey(), lastValues.get(entry.getKey()));
      }
    }
    discriminator = discriminator == null ? "" : discriminator;
    eventRegexpListeners.computeIfAbsent(discriminator, d -> new ConcurrentHashMap<>()).put(regexp, listener);
    return this;
  }

  @Override
  public ContextEvent fireEventIfNotSame(@NotNull String key, @Nullable State value) {
    return fireEvent(key, value, true);
  }

  @Override
  public @NotNull ContextEvent fireEvent(@NotNull String key, @Nullable State value) {
    return fireEvent(key, value, false);
  }

  @Override
  public @NotNull ContextEvent removeEntityUpdateListener(@NotNull String entityID, @NotNull String key) {
    if (this.entityUpdateListeners.idListeners.containsKey(entityID)) {
      this.entityUpdateListeners.idListeners.get(entityID).remove(key);
    }
    if (this.entityUpdateListeners.idBiListeners.containsKey(entityID)) {
      this.entityUpdateListeners.idBiListeners.get(entityID).remove(key);
    }
    return this;
  }

  @Override
  public @NotNull ContextEvent removeEntityRemoveListener(@NotNull String entityID, @NotNull String key) {
    if (this.entityRemoveListeners.idListeners.containsKey(entityID)) {
      this.entityRemoveListeners.idListeners.get(entityID).remove(key);
    }
    return this;
  }

  @Override
  public void runOnceOnInternetUp(@NotNull String name, @NotNull ThrowingRunnable<Exception> command) {
    context.bgp().getInternetAvailabilityService().addRunOnceOnInternetUpListener(name, command);
  }

  @Override
  public boolean isInternetUp() {
    return context.bgp().getInternetAvailabilityService().getInternetUp().get();
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityStatusUpdateListener(
    @NotNull String entityID, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityUpdateStatusListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityUpdateStatusListeners.idListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityUpdateListener(
    @NotNull String entityID, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityUpdateListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityUpdateListeners.idListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityUpdateListener(
    @NotNull String entityID, @NotNull String key, @NotNull EntityUpdateListener<T> listener) {
    this.entityUpdateListeners.idBiListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityUpdateListeners.idBiListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityUpdateListener(
    @NotNull Class<T> entityClass, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityUpdateListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityUpdateListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityUpdateListener(
    @NotNull Class<T> entityClass, @NotNull String key, @NotNull EntityUpdateListener<T> listener) {
    this.entityUpdateListeners.typeBiListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityUpdateListeners.typeBiListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityCreateListener(
    @NotNull Class<T> entityClass, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityCreateListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityCreateListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityRemovedListener(
    @NotNull String entityID, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityRemoveListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
    this.entityRemoveListeners.idListeners.get(entityID).put(key, listener);
    return this;
  }

  @Override
  public <T extends BaseEntityIdentifier> @NotNull ContextEvent addEntityRemovedListener(
    @NotNull Class<T> entityClass, @NotNull String key, @NotNull Consumer<T> listener) {
    this.entityRemoveListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
    this.entityRemoveListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    return this;
  }

  @Override
  public int getEventCount(@NotNull String key) {
    int count = 0;
    for (Map<String, Consumer<State>> item : eventListeners.values()) {
      if (item.containsKey(key)) {
        count++;
      }
    }
    return count;
  }

  public void onContextCreated() throws Exception {
    // execute all updates in thread
    new Thread(() -> {
      while (true) {
        try {
          EntityUpdate entityUpdate = entityUpdatesQueue.take();
          entityUpdate.itemAction.handler.accept(context, entityUpdate.entity);
        } catch (Exception ex) {
          log.error("Error while execute postUpdate action", ex);
        }
      }
    }, "EntityChangeHandler").start();
    // event handler
    new Thread(() -> {
      while (true) {
        try {
          Event event = eventQueue.take();
          for (Map<String, Consumer<State>> eventListenerMap : eventListeners.values()) {
            Consumer<State> listener = eventListenerMap.get(event.key);
            if (listener != null) {
              listener.accept(event.value);
            }
          }
          for (Map<Pattern, BiConsumer<String, State>> entry : eventRegexpListeners.values()) {
            for (Entry<Pattern, BiConsumer<String, State>> options : entry.entrySet()) {
              if (options.getKey().matcher(event.key).matches()) {
                options.getValue().accept(event.key, event.value);
              }
            }
          }
          globalEvenListeners.forEach(l -> l.accept(event.key, event.value));
          context.fireAllLock(lockManager -> lockManager.signalAll(event.key, event.value));
        } catch (Exception ex) {
          log.error("Error while execute event handler", ex);
        }
      }
    }, "EventHandler").start();
  }

  private @NotNull ContextEventImpl fireEvent(@NotNull String key, @Nullable State value, boolean compareValues) {
    // fire by key and key + value type
    fireEventInternal(key, value, compareValues);
        /*if (value != null && !(value instanceof String)) {
            fireEventInternal(key + "_" + value.getClass().getSimpleName(), value, compareValues);
        }*/
    return this;
  }

  private void fireEventInternal(@NotNull String key, @Nullable State value, boolean compareValues) {
    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("Unable to fire event with empty key");
    }
    if (value != null) {
      if (compareValues && Objects.equals(value, lastValues.get(key))) {
        return;
      }
      lastValues.put(key, value);
    }
    eventQueue.add(new Event(key, value));
  }

  public void registerEntityListeners(EntityManagerFactory entityManagerFactory) {
    SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
    EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
    if (registry == null) {
      throw new IllegalStateException("Unable to find EventListenerRegistry.class");
    }

    registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(event -> {
      Object entity = event.getEntity();
      if (entity instanceof BaseEntity baseEntity) {
        baseEntity.setContext(context);
        baseEntity.afterFetch();
        loadEntityService(context, entity);
      }
    });
    registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(event -> {
      Object entity = event.getEntity();
      if (entity instanceof BaseEntity baseEntity) {
        baseEntity.setContext(context);

        UserEntity user = context.user().getLoggedInUser();
        if (user != null && !context.user().isAdminLoggedUser()) {
          UserGuestEntity guest = (UserGuestEntity) user;
          guest.assertDeleteAccess(baseEntity);
        }

        if (baseEntity.isDisableDelete() && !context.user().isSuperAdmin()) {
          context.ui().toastr().error("User is not allowed to delete entity");
          throw new IllegalStateException("Unable to delete entity");
        }
        baseEntity.beforeDelete();
      }
      return false;
    });
    registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(new PostInsertEventListenerStandardImpl() {
      @Override
      public void onPostInsert(PostInsertEvent event) {
        super.onPostInsert(event);
        updateCacheEntity(context, event.getEntity(), ItemAction.Insert);
        entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Insert));
      }
    });
    registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(new PostUpdateEventListenerStandardImpl() {
      @Override
      public void onPostUpdate(PostUpdateEvent event) {
        super.onPostUpdate(event);
        Object entity = event.getEntity();
        EventSource eventSource = event.getSession();
        EntityEntry entry = eventSource.getPersistenceContextInternal().getEntry(entity);
        if (org.hibernate.engine.spi.Status.DELETED == entry.getStatus()) {
          entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Delete));
        } else {
          updateCacheEntity(context, event.getEntity(), ItemAction.Update);
          entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Update));
        }
      }
    });

    registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(new PostDeleteEventListenerStandardImpl() {
      @Override
      public void onPostDelete(PostDeleteEvent event) {
        super.onPostDelete(event);
        if (event.getEntity() instanceof BaseEntity baseEntity) {
          baseEntity.setContext(context);
          updateCacheEntity(context, event.getEntity(), ItemAction.Remove);
        }
        entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Delete));
      }
    });
  }

  @AllArgsConstructor
  public enum EntityUpdateAction {
    Insert((context, entity) -> {
      postInsertUpdate(context, entity, true);
      if (entity instanceof BaseEntity) {
        context.ui().toastr().success(Lang.getServerMessage("ENTITY_CREATED", ((BaseEntity) entity).getEntityID()));
      }
    }),
    Update((context, entity) -> postInsertUpdate(context, entity, false)),
    Delete((context, entity) -> {
      if (entity instanceof BaseEntity be) {
        // execute in separate thread
        context.bgp().builder("delete-delay-entity-" + be.getEntityID())
          .execute(() -> {
            be.afterDelete();
            context.var().deleteGroup(be.getEntityID());
            if (be instanceof DeviceBaseEntity dbe) {
              context.var().deleteGroup(dbe.getIeeeAddress());
            }
            // destroy any additional services
            if (be instanceof EntityService) {
              try {
                ((EntityService<?>) be).destroyService(null);
              } catch (Exception ex) {
                log.warn("Unable to destroy service for entity: {}", getTitle());
              }
            }
            be.afterDelete();

            String entityID = be.getEntityID();
            // remove all status for entity
            ContextStorageImpl.ENTITY_MEMORY_MAP.remove(entityID);
            // remove in-memory data if any exists
            InMemoryDB.removeService(entityID);
            // clear all registered console plugins if any exists
            context.ui().console().unRegisterPlugin(entityID);
            // remove any registered notifications/notification block
            context.ui().notification().removeBlock(entityID);
            // remove in-memory data
            context.db().deleteInMemoryData(entityID);
          });
      }
      if (entity instanceof WorkspaceVariable wv) {
        // fire update if variable was removed
        context.sendEntityUpdateNotification(wv.getTopGroup(), ItemAction.Update);
      } else {
        context.sendEntityUpdateNotification(entity, ItemAction.Remove);
      }
    });

    private final ThrowingBiConsumer<ContextImpl, Object, Exception> handler;
  }

  private record EventTTL(String key, String discriminator, Duration ttl, long created) {
  }

  @Getter
  public static class EntityListener {

    private final Map<String, Map<String, EntityUpdateListener>> typeBiListeners = new HashMap<>();
    private final Map<String, Map<String, Consumer>> typeListeners = new HashMap<>();

    private final Map<String, Map<String, Consumer>> idListeners = new HashMap<>();
    private final Map<String, Map<String, EntityUpdateListener>> idBiListeners = new HashMap<>();

    public <T extends HasEntityIdentifier> void notify(T saved, T oldEntity) {
      // notify by entityID
      String entityId = saved == null ? oldEntity.getEntityID() : saved.getEntityID();
      for (Consumer listener : idListeners.getOrDefault(entityId, emptyMap()).values()) {
        listener.accept(saved);
      }
      for (EntityUpdateListener listener : idBiListeners.getOrDefault(entityId, emptyMap()).values()) {
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

    private <T extends HasEntityIdentifier> void notifyByType(String name, T saved, T oldEntity) {
      for (EntityUpdateListener listener : typeBiListeners.getOrDefault(name, emptyMap()).values()) {
        listener.entityUpdated(saved, oldEntity);
      }
      for (Consumer listener : typeListeners.getOrDefault(name, emptyMap()).values()) {
        listener.accept(saved == null ? oldEntity : saved); // for Delete we have to use oldEntity
      }
    }
  }

  private record Event(String key, State value) {
  }

  private record EntityUpdate(Object entity, EntityUpdateAction itemAction) {
  }
}

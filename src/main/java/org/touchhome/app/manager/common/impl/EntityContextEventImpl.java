package org.touchhome.app.manager.common.impl;

import static java.util.Collections.emptyMap;
import static org.apache.xmlbeans.XmlBeans.getTitle;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingRunnable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.persistence.EntityManagerFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.bgp.InternetAvailabilityBgpService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.EntityContextImpl.ItemAction;
import org.touchhome.app.manager.common.EntityContextStorage;
import org.touchhome.bundle.api.EntityContextEvent;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.BaseEntityIdentifier;
import org.touchhome.bundle.api.entity.PinBaseEntity;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.service.EntityService.ServiceInstance;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Lang;

@Log4j2
public class EntityContextEventImpl implements EntityContextEvent {

    @Getter private final EntityListener entityUpdateListeners = new EntityListener();

    @Getter private final EntityListener entityCreateListeners = new EntityListener();

    @Getter private final EntityListener entityRemoveListeners = new EntityListener();

    @Getter private final Set<OptionModel> events = new HashSet<>();
    private final Map<String, Object> lastValues = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Consumer<Object>>> eventListeners = new ConcurrentHashMap<>();

    @Getter private final List<BiConsumer<String, Object>> globalEvenListeners = new ArrayList<>();

    // constructor parameters
    private final EntityContextImpl entityContext;

    private final BlockingQueue<EntityUpdate> entityUpdatesQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();

    public EntityContextEventImpl(EntityContextImpl entityContext, EntityManagerFactory entityManagerFactory) {
        this.entityContext = entityContext;
        registerEntityListeners(entityManagerFactory);

        // execute all updates in thread
        new Thread(() -> {
            while (true) {
                try {
                    EntityUpdate entityUpdate = entityUpdatesQueue.take();
                    entityUpdate.itemAction.handler.accept(entityContext, entityUpdate.entity);
                } catch (Exception ex) {
                    log.error("Error while execute postUpdate action", ex);
                }
            }
        }).start();
        // event handler
        new Thread(() -> {
            while (true) {
                try {
                    Event event = eventQueue.take();
                    for (Map<String, Consumer<Object>> eventListenerMap : eventListeners.values()) {
                        if (eventListenerMap.containsKey(event.key)) {
                            eventListenerMap.get(event.key).accept(event.value);
                        }
                    }
                    globalEvenListeners.forEach(l -> l.accept(event.key, event.value));
                    entityContext.fireAllBroadcastLock(broadcastLockManager -> broadcastLockManager.signalAll(event.key, event.value));
                } catch (Exception ex) {
                    log.error("Error while execute event handler", ex);
                }
            }
        }).start();
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
        eventListeners.computeIfAbsent(discriminator, d -> new ConcurrentHashMap<>()).put(key, listener);
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
        eventQueue.add(new Event(key, value));
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
        InternetAvailabilityBgpService.addRunOnceOnInternetUpListener(name, command);
    }

    @Override
    public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(String entityID, String key, Consumer<T> listener) {
        this.entityUpdateListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
        this.entityUpdateListeners.idListeners.get(entityID).put(key, listener);
        return this;
    }

    @Override
    public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(String entityID, String key, EntityUpdateListener<T> listener) {
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
    public <T extends BaseEntityIdentifier> EntityContextEvent addEntityUpdateListener(Class<T> entityClass, String key, EntityUpdateListener<T> listener) {
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
    public <T extends BaseEntityIdentifier> EntityContextEvent addEntityRemovedListener(Class<T> entityClass, String key, Consumer<T> listener) {
        this.entityRemoveListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
        this.entityRemoveListeners.typeListeners.get(entityClass.getName()).put(key, listener);
        return this;
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

        private <T extends HasEntityIdentifier> void notifyByType(String name, T saved, T oldEntity) {
            for (EntityUpdateListener listener : typeBiListeners.getOrDefault(name, emptyMap()).values()) {
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

        public int getCount(String key) {
            return 0;
        }
    }

    private void registerEntityListeners(EntityManagerFactory entityManagerFactory) {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(event -> {
            Object entity = event.getEntity();
            if (entity instanceof BaseEntity) {
                loadEntityService(entityContext, entity);
            }
        });
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener((PreDeleteEventListener) event -> {
            Object entity = event.getEntity();
            if (entity instanceof BaseEntity) {
                BaseEntity baseEntity = (BaseEntity) entity;
                baseEntity.beforeDelete(entityContext);
            }
            return false;
        });
        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(new PostInsertEventListenerStandardImpl() {
            @Override
            public void onPostInsert(PostInsertEvent event) {
                super.onPostInsert(event);
                updateCacheEntity(entityContext, event.getEntity(), ItemAction.Insert);
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
                    updateCacheEntity(entityContext, event.getEntity(), ItemAction.Update);
                    entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Update));
                }
            }
        });

        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(new PostDeleteEventListenerStandardImpl() {
            @Override
            public void onPostDelete(PostDeleteEvent event) {
                super.onPostDelete(event);
                updateCacheEntity(entityContext, event.getEntity(), ItemAction.Remove);
                entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Delete));
            }
        });
    }

    private static void postInsertUpdate(EntityContextImpl entityContext, Object entity, boolean persist) {
        if (entity instanceof BaseEntity) {
            loadEntityService(entityContext, entity);
            ((BaseEntity) entity).afterUpdate(entityContext, persist);
            // Do not send updates to UI in case of Status.DELETED
            entityContext.sendEntityUpdateNotification(entity, persist ? ItemAction.Insert : ItemAction.Update);
        }
    }

    // Try to instantiate service associated with entity
    private static void loadEntityService(EntityContextImpl entityContext, Object entity) {
        if (entity instanceof EntityService) {
            Optional<?> serviceOptional = ((EntityService<?, ?>) entity).getOrCreateService(entityContext);
            // Update entity into service
            if (serviceOptional.isPresent()) {
                try {
                    EntityService.ServiceInstance service = (ServiceInstance) serviceOptional.get();
                    if (service.entityUpdated((EntityService) entity)) {
                        if (service.testService()) {
                            ((EntityService<?, ?>) entity).setStatusOnline();
                        }
                    }
                } catch (Exception ex) {
                    ((EntityService<?, ?>) entity).setStatusError(ex);
                }
            }
        }
    }

    private static void updateCacheEntity(EntityContextImpl entityContext, Object entity, ItemAction type) {
        try {
            if (entity instanceof BaseEntity) {
                entityContext.getCacheService().entityUpdated((BaseEntity) entity);
            }
        } catch (Exception ex) {
            log.error("Unable to update cache entity <{}> for entity: <{}>. Msg: <{}>", type, entity, CommonUtils.getErrorMessage(ex));
        }
    }

    @RequiredArgsConstructor
    private static class Event {

        private final String key;
        private final Object value;
    }

    @RequiredArgsConstructor
    private static class EntityUpdate {

        private final Object entity;
        private final EntityUpdateAction itemAction;
    }

    @AllArgsConstructor
    public enum EntityUpdateAction {
        Insert((context, entity) -> {
            postInsertUpdate(context, entity, true);
            if (entity instanceof BaseEntity && !(entity instanceof PinBaseEntity)) {
                context.ui().sendSuccessMessage(Lang.getServerMessage("ENTITY_CREATED", "NAME", ((BaseEntity<?>) entity).getEntityID()));
            }
        }),
        Update((context, entity) -> {
            postInsertUpdate(context, entity, false);
        }),
        Delete((context, entity) -> {
            if (entity instanceof BaseEntity) {
                String entityID = ((BaseEntity<?>) entity).getEntityID();
                // remove all status for entity
                EntityContextStorage.ENTITY_MEMORY_MAP.remove(entityID);
                // remove in-memory data if any exists
                InMemoryDB.removeService(entityID);
                // clear all registered console plugins if any exists
                context.ui().unRegisterConsolePlugin(entityID);
                // destroy any additional services
                if (entity instanceof EntityService) {
                    try {
                        ((EntityService<?, ?>) entity).destroyService();
                    } catch (Exception ex) {
                        log.warn("Unable to destroy service for entity: {}", getTitle());
                    }
                }
                // remove in-memory data
                context.getEntityContextStorage().remove(entityID);
                ((BaseEntity) entity).afterDelete(context);
            }
            context.sendEntityUpdateNotification(entity, ItemAction.Remove);
        });

        private final ThrowingBiConsumer<EntityContextImpl, Object, Exception> handler;
    }
}

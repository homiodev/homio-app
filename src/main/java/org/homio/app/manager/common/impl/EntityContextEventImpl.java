package org.homio.app.manager.common.impl;

import static java.util.Collections.emptyMap;
import static org.apache.xmlbeans.XmlBeans.getTitle;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingRunnable;
import jakarta.persistence.EntityManagerFactory;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextEvent;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.BaseEntityIdentifier;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.service.EntityService;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.EntityContextImpl.ItemAction;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.service.mem.InMemoryDB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    private final Map<String, UdpContext> listenUdpMap = new HashMap<>();

    // constructor parameters
    private final EntityContextImpl entityContext;

    private final BlockingQueue<EntityUpdate> entityUpdatesQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();

    public EntityContextEventImpl(EntityContextImpl entityContext, EntityManagerFactory entityManagerFactory) {
        this.entityContext = entityContext;
        registerEntityListeners(entityManagerFactory);
    }

    @Override
    public synchronized void removeEvents(String key, String... additionalKeys) {
        eventListeners.remove(key);
        lastValues.remove(key);
        for (String additionalKey : additionalKeys) {
            eventListeners.remove(additionalKey);
            lastValues.remove(additionalKey);
        }
    }

    @Override
    public synchronized void removeEventListener(String discriminator, String key) {
        Map<String, Consumer<Object>> map = eventListeners.get(discriminator);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) {
                eventListeners.remove(discriminator);
            }
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
        entityContext.bgp().getInternetAvailabilityService().addRunOnceOnInternetUpListener(name, command);
    }

    @Override
    public boolean isInternetUp() {
        return entityContext.bgp().getInternetAvailabilityService().getInternetUp().get();
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

    @Override
    @SneakyThrows
    public void listenUdp(
        String key, String host, int port, BiConsumer<DatagramPacket, String> listener) {
        String hostPortKey = (host == null ? "0.0.0.0" : host) + ":" + port;
        if (!this.listenUdpMap.containsKey(hostPortKey)) {
            EntityContextBGP.ThreadContext<Void> scheduleFuture;
            try {
                DatagramSocket socket = new DatagramSocket(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
                DatagramPacket datagramPacket = new DatagramPacket(new byte[255], 255);

                scheduleFuture = entityContext.bgp().builder("listen-udp-" + hostPortKey).execute(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        socket.receive(datagramPacket);
                        byte[] data = datagramPacket.getData();
                        String text = new String(data, 0, datagramPacket.getLength());
                        listenUdpMap.get(hostPortKey).handle(datagramPacket, text);
                    }
                });
                scheduleFuture.setDescription("Listen udp: " + hostPortKey);
            } catch (Exception ex) {
                entityContext.ui().addOrUpdateNotificationBlock("UPD", "UDP", new Icon("fas fa-kip-sign", "#482594"), blockBuilder -> {
                    String info = Lang.getServerMessage("UDP_ERROR", FlowMap.of("key", hostPortKey, "msg", ex.getMessage()));
                    blockBuilder.addInfo(info, new Icon("fas fa-triangle-exclamation"));
                });
                log.error("Unable to listen udp host:port: <{}>", hostPortKey);
                return;
            }
            this.listenUdpMap.put(hostPortKey, new UdpContext(scheduleFuture));
        }
        this.listenUdpMap.get(hostPortKey).put(key, listener);
    }

    public void stopListenUdp(String key) {
        for (UdpContext udpContext : this.listenUdpMap.values()) {
            udpContext.cancel(key);
        }
    }

    public void onContextCreated() throws Exception {
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

    private void registerEntityListeners(EntityManagerFactory entityManagerFactory) {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(event -> {
            Object entity = event.getEntity();
            if (entity instanceof BaseEntity baseEntity) {
                baseEntity.setEntityContext(entityContext);
                baseEntity.afterFetch();
                loadEntityService(entityContext, entity);
            }
        });
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(event -> {
            Object entity = event.getEntity();
            if (entity instanceof BaseEntity baseEntity) {
                baseEntity.setEntityContext(entityContext);
                if (baseEntity.isDisableDelete()) {
                    throw new IllegalStateException("Unable to remove entity");
                }
                baseEntity.beforeDelete();
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
                if (event.getEntity() instanceof BaseEntity baseEntity) {
                    baseEntity.setEntityContext(entityContext);
                    baseEntity.afterDelete();
                    updateCacheEntity(entityContext, event.getEntity(), ItemAction.Remove);
                }
                entityUpdatesQueue.add(new EntityUpdate(event.getEntity(), EntityUpdateAction.Delete));
            }
        });
    }

    private static void postInsertUpdate(EntityContextImpl entityContext, Object entity, boolean persist) {
        if (entity instanceof BaseEntity baseEntity) {
            baseEntity.setEntityContext(entityContext);
            loadEntityService(entityContext, entity);
            if (persist) {
                baseEntity.afterUpdate();
            } else {
                baseEntity.afterPersist();
            }
            // corner case if save/update WorkspaceVariable
            if (entity instanceof WorkspaceVariable wv) {
                WorkspaceGroup group = wv.getWorkspaceGroup();
                if (group.getParent() != null) {
                    group = group.getParent();
                }
                entity = group;
            }
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
                    service.entityUpdated((EntityService) entity);
                } catch (Exception ex) {
                    ((EntityService<?, ?>) entity).setStatusError(ex);
                }
            }
        }
    }

    /**
     * Require to run inside transaction! to load lazy loaded groups/parents/etc...
     */
    private static void updateCacheEntity(EntityContextImpl entityContext, Object entity, ItemAction type) {
        try {
            if (entity instanceof BaseEntity) {
                entityContext.getCacheService().entityUpdated((BaseEntity) entity);
            }
        } catch (Exception ex) {
            log.error("Unable to update cache entity <{}> for entity: <{}>. Msg: <{}>", type, entity, CommonUtils.getErrorMessage(ex));
        }
    }

    @AllArgsConstructor
    public enum EntityUpdateAction {
        Insert((context, entity) -> {
            postInsertUpdate(context, entity, true);
            if (entity instanceof BaseEntity) {
                context.ui().sendSuccessMessage(Lang.getServerMessage("ENTITY_CREATED", ((BaseEntity) entity).getEntityID()));
            }
        }),
        Update((context, entity) -> postInsertUpdate(context, entity, false)),
        Delete((context, entity) -> {
            if (entity instanceof BaseEntity) {
                // execute in separate thread
                context.bgp().builder("delete-delay-entity-" + ((BaseEntity) entity).getEntityID())
                       .execute(() -> {
                           // destroy any additional services
                           if (entity instanceof EntityService) {
                               try {
                                   ((EntityService<?, ?>) entity).destroyService();
                               } catch (Exception ex) {
                                   log.warn("Unable to destroy service for entity: {}", getTitle());
                               }
                           }
                           ((BaseEntity) entity).afterDelete();

                           String entityID = ((BaseEntity) entity).getEntityID();
                           // remove all status for entity
                           EntityContextStorageImpl.ENTITY_MEMORY_MAP.remove(entityID);
                           // remove in-memory data if any exists
                           InMemoryDB.removeService(entityID);
                           // clear all registered console plugins if any exists
                           context.ui().unRegisterConsolePlugin(entityID);
                           // remove any registered notifications/notification block
                           context.ui().removeNotificationBlock(entityID);
                           // remove in-memory data
                           context.getEntityContextStorageImpl().remove(entityID);
                       });
            }
            context.sendEntityUpdateNotification(entity, ItemAction.Remove);
        });

        private final ThrowingBiConsumer<EntityContextImpl, Object, Exception> handler;
    }

    @RequiredArgsConstructor
    private static class UdpContext {

        private final Map<String, BiConsumer<DatagramPacket, String>> keyToListener = new HashMap<>();
        private final EntityContextBGP.ThreadContext<Void> scheduleFuture;

        public void handle(DatagramPacket datagramPacket, String text) {
            for (BiConsumer<DatagramPacket, String> listener : keyToListener.values()) {
                listener.accept(datagramPacket, text);
            }
        }

        public void put(String key, BiConsumer<DatagramPacket, String> listener) {
            this.keyToListener.put(key, listener);
        }

        public void cancel(String key) {
            keyToListener.remove(key);
            if (keyToListener.isEmpty()) {
                scheduleFuture.cancel();
            }
        }
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

        public int getCount(String key) {
            return 0;
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

    private record Event(String key, Object value) {

    }

    private record EntityUpdate(Object entity, EntityUpdateAction itemAction) {

    }
}

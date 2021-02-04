package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextEvent;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EntityContextEventImpl implements EntityContextEvent {
    private final BroadcastLockManager broadcastLockManager;

    @Getter
    private final EntityListener entityUpdateListeners = new EntityListener();

    @Getter
    private final EntityListener entityRemoveListeners = new EntityListener();

    @Getter
    private final Map<OptionModel, Object> events = new HashMap<>();
    private final Map<String, Object> lastValues = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Object>> listeners = new HashMap<>();

    public EntityContextEventImpl(BroadcastLockManager broadcastLockManager) {
        this.broadcastLockManager = broadcastLockManager;
        this.addEvent("internet-down", "Internet down");
        this.addEvent("internet-up", "Internet up");
        this.addEvent("app-release", "Found new app release");
    }

    @Override
    public void removeEvents(String... keys) {

    }

    @Override
    public void setListener(String key, Consumer<Object> listener) {
        if (events.containsKey(OptionModel.key(key))) {
            listeners.put(key, listener);
        } else {
            throw new IllegalArgumentException("Unable to listen unknown event: " + key);
        }
    }

    @Override
    public void fireEvent(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value != null) {
            if (Objects.equals(value, lastValues.get(key))) {
                return;
            }
            lastValues.put(key, value);
        }

        Consumer<Object> consumer = listeners.get(key);
        if (consumer != null) {
            consumer.accept(value);
        }
        broadcastLockManager.signalAll(key, value);
    }

    @Override
    public String addEvent(String key, String name) {
        this.events.put(OptionModel.of(key, name), "");
        return key;
    }

    @Override
    public String addEventAndFire(String key, String name, Object value) {
        this.events.put(OptionModel.of(key, name), "");
        this.fireEvent(key, value);
        return key;
    }

    @Override
    public void removeEntityUpdateListener(String entityID, String key) {
        if (this.entityUpdateListeners.idListeners.containsKey(entityID)) {
            this.entityUpdateListeners.idListeners.get(entityID).remove(key);
        }
        if (this.entityUpdateListeners.idBiListeners.containsKey(entityID)) {
            this.entityUpdateListeners.idBiListeners.get(entityID).remove(key);
        }
    }

    @Override
    public void removeEntityRemoveListener(String entityID, String key) {
        if (this.entityRemoveListeners.idListeners.containsKey(entityID)) {
            this.entityRemoveListeners.idListeners.get(entityID).remove(key);
        }
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, String key, Consumer<T> listener) {
        this.entityUpdateListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
        this.entityUpdateListeners.idListeners.get(entityID).put(key, listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(String entityID, String key, EntityContext.EntityUpdateListener<T> listener) {
        this.entityUpdateListeners.idBiListeners.putIfAbsent(entityID, new HashMap<>());
        this.entityUpdateListeners.idBiListeners.get(entityID).put(key, listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, String key, Consumer<T> listener) {
        this.entityUpdateListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
        this.entityUpdateListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, String key, EntityContext.EntityUpdateListener<T> listener) {
        this.entityUpdateListeners.typeBiListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
        this.entityUpdateListeners.typeBiListeners.get(entityClass.getName()).put(key, listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(String entityID, String key, Consumer<T> listener) {
        this.entityRemoveListeners.idListeners.putIfAbsent(entityID, new HashMap<>());
        this.entityRemoveListeners.idListeners.get(entityID).put(key, listener);
    }

    @Override
    public <T extends BaseEntity> void addEntityRemovedListener(Class<T> entityClass, String key, Consumer<T> listener) {
        this.entityRemoveListeners.typeListeners.putIfAbsent(entityClass.getName(), new HashMap<>());
        this.entityRemoveListeners.typeListeners.get(entityClass.getName()).put(key, listener);
    }

    @Getter
    public static class EntityListener {
        private final Map<String, Map<String, EntityContext.EntityUpdateListener>> typeBiListeners = new HashMap<>();
        private final Map<String, Map<String, Consumer>> typeListeners = new HashMap<>();

        private final Map<String, Map<String, Consumer>> idListeners = new HashMap<>();
        private final Map<String, Map<String, EntityContext.EntityUpdateListener>> idBiListeners = new HashMap<>();

        public <T extends HasEntityIdentifier> void notify(T saved, T oldEntity) {
            // notify by entityID
            for (Consumer listener : idListeners.getOrDefault(saved.getEntityID(), Collections.emptyMap()).values()) {
                listener.accept(saved);
            }
            for (EntityContext.EntityUpdateListener listener : idBiListeners.getOrDefault(saved.getEntityID(), Collections.emptyMap()).values()) {
                listener.entityUpdated(saved, oldEntity);
            }

            // notify by class type
            for (Class<?> entityClass : ClassFinder.findAllParentClasses(saved.getClass(), BaseEntity.class)) {
                this.notifyByType(entityClass.getName(), saved, oldEntity);
            }
        }

        private <T extends HasEntityIdentifier> void notifyByType(String name, T saved, T oldEntity) {
            for (EntityContext.EntityUpdateListener listener : typeBiListeners.getOrDefault(name, Collections.emptyMap()).values()) {
                listener.entityUpdated(saved, oldEntity);
            }
            for (Consumer listener : typeListeners.getOrDefault(name, Collections.emptyMap()).values()) {
                listener.accept(saved);
            }
        }

        public <T extends BaseEntity> boolean isRequireFetchOldEntity(T entity) {
            if (!idBiListeners.getOrDefault(entity.getEntityID(), Collections.emptyMap()).isEmpty()) {
                return true;
            }
            Class<?> cursor = entity.getClass();
            while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
                if (!typeBiListeners.getOrDefault(cursor.getName(), Collections.emptyMap()).isEmpty()) {
                    return true;
                }
                cursor = cursor.getSuperclass();
            }
            return false;
        }
    }
}

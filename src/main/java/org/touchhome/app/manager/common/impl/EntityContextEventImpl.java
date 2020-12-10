package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import org.touchhome.bundle.api.EntityContextEvent;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EntityContextEventImpl implements EntityContextEvent {
    private final BroadcastLockManager broadcastLockManager;

    @Getter
    private final Map<Option, Object> events = new HashMap<>();
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
        if (events.containsKey(Option.key(key))) {
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
        this.events.put(Option.of(key, name), "");
        return key;
    }

    @Override
    public String addEventAndFire(String key, String name, Object value) {
        this.events.put(Option.of(key, name), "");
        this.fireEvent(key, value);
        return key;
    }
}

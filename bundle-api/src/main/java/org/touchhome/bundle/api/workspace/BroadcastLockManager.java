package org.touchhome.bundle.api.workspace;

import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log4j2
@Component
public class BroadcastLockManager {

    private Map<String, Holder> warehouse = new HashMap<>();
    private final Runnable runnable = () -> {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            for (Map.Entry<String, Pair<BroadcastLock, Supplier<Boolean>>> item : listenerHolder.broadcastListenersMap.entrySet()) {
                if (item.getValue().getSecond().get()) {
                    item.getValue().getFirst().signalAll();
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }
    };

    public void signalAll(String key) {
        for (Holder holder : warehouse.values()) {
            if (holder.broadcastListeners.containsKey(key))
                holder.broadcastListeners.get(key).signalAll();
        }
    }

    public BroadcastLock getOrCreateLock(String key) {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        return listenerHolder.broadcastListeners.computeIfAbsent(key, k -> new BroadcastLock(key));
    }

    public BroadcastLock listenEvent(WorkspaceBlock workspaceBlock, Supplier<Boolean> supplier) {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        BroadcastLock lock = getOrCreateLock(workspaceBlock.getId());
        listenerHolder.broadcastListenersMap.put(workspaceBlock.getId(), Pair.of(lock, supplier));
        if (listenerHolder.thread == null) {
            listenerHolder.thread = new Thread(runnable, Thread.currentThread().getName());
            listenerHolder.thread.start();
        }
        return lock;
    }

    public void release(String id) {
        warehouse.putIfAbsent(id, new Holder());

        Holder listenerHolder = warehouse.get(id);
        listenerHolder.broadcastListenersMap.clear();
        listenerHolder.broadcastListeners.clear();
        if (listenerHolder.thread != null) {
            listenerHolder.thread.interrupt();
            listenerHolder.thread = null;
        }
    }

    private static class Holder {
        private final Map<String, BroadcastLock> broadcastListeners = new ConcurrentHashMap<>();
        private final Map<String, Pair<BroadcastLock, Supplier<Boolean>>> broadcastListenersMap = new ConcurrentHashMap<>();
        private Thread thread;
    }
}

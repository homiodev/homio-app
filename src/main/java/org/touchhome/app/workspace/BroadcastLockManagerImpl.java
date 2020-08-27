package org.touchhome.app.workspace;

import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log4j2
@Component
public class BroadcastLockManagerImpl implements BroadcastLockManager {

    private Map<String, Holder> warehouse = new HashMap<>();

    private final Runnable runnable = () -> {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            for (Map.Entry<String, Pair<BroadcastLockImpl, Supplier<Boolean>>> item : listenerHolder.broadcastListenersMap.entrySet()) {
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

    @Override
    public void signalAll(String key, Object value) {
        for (Holder holder : warehouse.values()) {
            if (holder.broadcastListeners.containsKey(key)) {
                holder.broadcastListeners.get(key).forEach(a -> a.signalAll(value));
            }
        }
    }

    @Override
    public BroadcastLockImpl getOrCreateLock(WorkspaceBlock workspaceBlock, String key, Object expectedValue) {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        BroadcastLockImpl lock = new BroadcastLockImpl(key, expectedValue);
        listenerHolder.broadcastListeners.putIfAbsent(key, new ArrayList<>());
        listenerHolder.broadcastListeners.get(key).add(lock);
        ((WorkspaceBlockImpl) workspaceBlock).addLock(lock);
        return lock;
    }

    @Override
    public BroadcastLockImpl getOrCreateLock(WorkspaceBlock workspaceBlock) {
        return getOrCreateLock(workspaceBlock, workspaceBlock.getId(), null);
    }

    @Override
    public BroadcastLockImpl getOrCreateLock(WorkspaceBlock workspaceBlock, String key) {
        return getOrCreateLock(workspaceBlock, key, null);
    }

    @Override
    public BroadcastLockImpl listenEvent(WorkspaceBlock workspaceBlock, Supplier<Boolean> supplier) {
        Holder listenerHolder = warehouse.get(Thread.currentThread().getName());
        BroadcastLockImpl lock = getOrCreateLock(workspaceBlock);
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

        for (Pair<BroadcastLockImpl, Supplier<Boolean>> pair : listenerHolder.broadcastListenersMap.values()) {
            pair.getFirst().release();
        }
        listenerHolder.broadcastListenersMap.clear();

        for (List<BroadcastLockImpl> locks : listenerHolder.broadcastListeners.values()) {
            locks.forEach(BroadcastLockImpl::release);
        }
        listenerHolder.broadcastListeners.clear();

        if (listenerHolder.thread != null) {
            listenerHolder.thread.interrupt();
            listenerHolder.thread = null;
        }
    }

    private static class Holder {
        private final Map<String, List<BroadcastLockImpl>> broadcastListeners = new ConcurrentHashMap<>();
        private final Map<String, Pair<BroadcastLockImpl, Supplier<Boolean>>> broadcastListenersMap = new ConcurrentHashMap<>();
        private Thread thread;
    }
}

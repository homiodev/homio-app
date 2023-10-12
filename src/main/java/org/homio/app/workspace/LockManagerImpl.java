package org.homio.app.workspace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.LockManager;
import org.homio.api.workspace.WorkspaceBlock;

@Log4j2
@RequiredArgsConstructor
public class LockManagerImpl implements LockManager {

    private final String workspaceTabId;
    private final WorkspaceWarehouseContext workspaceWarehouse = new WorkspaceWarehouseContext();

    /**
     * Signal all lock with specified name and value
     */
    @Override
    public void signalAll(String key, Object value) {
        if (workspaceWarehouse.broadcastListeners.containsKey(key)) {
            workspaceWarehouse.broadcastListeners.get(key).forEach(a -> a.signalAll(value));
        }
    }

    /**
     * Create lock with unique key or get existed.
     */
    @Override
    public LockImpl getLock(WorkspaceBlock workspaceBlock, String key, Object expectedValue) {
        LockImpl lock = new LockImpl(key, expectedValue);
        workspaceWarehouse.broadcastListeners.putIfAbsent(key, new ArrayList<>());
        workspaceWarehouse.broadcastListeners.get(key).add(lock);
        ((WorkspaceBlockImpl) workspaceBlock).addLock(lock);
        return lock;
    }

    @Override
    public LockImpl getLock(WorkspaceBlock workspaceBlock) {
        return getLock(workspaceBlock, workspaceBlock.getId(), null);
    }

    @Override
    public LockImpl getLock(WorkspaceBlock workspaceBlock, String key) {
        return getLock(workspaceBlock, key, null);
    }

    @Override
    public Lock listenEvent(WorkspaceBlock workspaceBlock, Supplier<Boolean> supplier) {
        LockImpl lock = getLock(workspaceBlock);
        workspaceWarehouse.broadcastListenersMap.put(workspaceBlock.getId(), Pair.of(lock, supplier));

        if (workspaceWarehouse.threadContext == null) {
            workspaceBlock
                    .getEntityContext()
                    .bgp()
                    .builder("BroadcastListenEvent-" + workspaceTabId)
                    .interval(Duration.ofMillis(1000))
                    .tap(context -> workspaceWarehouse.threadContext = context)
                    .execute(() -> {
                        for (Entry<String, Pair<LockImpl, Supplier<Boolean>>> item : workspaceWarehouse.broadcastListenersMap.entrySet()) {
                            if (item.getValue().getValue().get()) {
                                item.getValue().getKey().signalAll();
                            }
                        }
                    });
        }

        return lock;
    }

    public void release() {
        for (Pair<LockImpl, Supplier<Boolean>> pair :
                workspaceWarehouse.broadcastListenersMap.values()) {
            pair.getKey().release();
        }
        workspaceWarehouse.broadcastListenersMap.clear();

        for (List<LockImpl> locks : workspaceWarehouse.broadcastListeners.values()) {
            locks.forEach(LockImpl::release);
        }
        workspaceWarehouse.broadcastListeners.clear();

        if (workspaceWarehouse.threadContext != null) {
            workspaceWarehouse.threadContext.cancel();
            workspaceWarehouse.threadContext = null;
        }
    }

    private static class WorkspaceWarehouseContext {

        private final Map<String, List<LockImpl>> broadcastListeners = new ConcurrentHashMap<>();
        private final Map<String, Pair<LockImpl, Supplier<Boolean>>> broadcastListenersMap = new ConcurrentHashMap<>();
        private ThreadContext<Object> threadContext;
    }
}

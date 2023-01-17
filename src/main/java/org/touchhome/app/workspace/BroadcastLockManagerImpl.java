package org.touchhome.app.workspace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;

@Log4j2
@RequiredArgsConstructor
public class BroadcastLockManagerImpl implements BroadcastLockManager {

    private final String workspaceTabId;
    private final WorkspaceWarehouseContext workspaceWarehouse = new WorkspaceWarehouseContext();

    @Override
    public void signalAll(String key, Object value) {
        if (workspaceWarehouse.broadcastListeners.containsKey(key)) {
            workspaceWarehouse.broadcastListeners.get(key).forEach(a -> a.signalAll(value));
        }
    }

    @Override
    public BroadcastLockImpl getOrCreateLock(
            WorkspaceBlock workspaceBlock, String key, Object expectedValue) {
        WorkspaceWarehouseContext listenerWorkspaceWarehouseContext = workspaceWarehouse;
        BroadcastLockImpl lock = new BroadcastLockImpl(key, expectedValue);
        listenerWorkspaceWarehouseContext.broadcastListeners.putIfAbsent(key, new ArrayList<>());
        listenerWorkspaceWarehouseContext.broadcastListeners.get(key).add(lock);
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
    public BroadcastLock listenEvent(WorkspaceBlock workspaceBlock, Supplier<Boolean> supplier) {
        BroadcastLockImpl lock = getOrCreateLock(workspaceBlock);
        workspaceWarehouse.broadcastListenersMap.put(
                workspaceBlock.getId(), Pair.of(lock, supplier));

        if (workspaceWarehouse.threadContext == null) {
            workspaceBlock
                    .getEntityContext()
                    .bgp()
                    .builder("BroadcastListenEvent-" + workspaceTabId)
                    .interval(Duration.ofMillis(1000))
                    .tap(context -> workspaceWarehouse.threadContext = context)
                    .execute(
                            () -> {
                                for (Entry<String, Pair<BroadcastLockImpl, Supplier<Boolean>>>
                                        item :
                                                workspaceWarehouse.broadcastListenersMap
                                                        .entrySet()) {
                                    if (item.getValue().getSecond().get()) {
                                        item.getValue().getFirst().signalAll();
                                    }
                                }
                            });
        }

        return lock;
    }

    public void release() {
        for (Pair<BroadcastLockImpl, Supplier<Boolean>> pair :
                workspaceWarehouse.broadcastListenersMap.values()) {
            pair.getFirst().release();
        }
        workspaceWarehouse.broadcastListenersMap.clear();

        for (List<BroadcastLockImpl> locks : workspaceWarehouse.broadcastListeners.values()) {
            locks.forEach(BroadcastLockImpl::release);
        }
        workspaceWarehouse.broadcastListeners.clear();

        if (workspaceWarehouse.threadContext != null) {
            workspaceWarehouse.threadContext.cancel();
            workspaceWarehouse.threadContext = null;
        }
    }

    private static class WorkspaceWarehouseContext {

        private final Map<String, List<BroadcastLockImpl>> broadcastListeners =
                new ConcurrentHashMap<>();
        private final Map<String, Pair<BroadcastLockImpl, Supplier<Boolean>>>
                broadcastListenersMap = new ConcurrentHashMap<>();
        private ThreadContext<Object> threadContext;
    }
}

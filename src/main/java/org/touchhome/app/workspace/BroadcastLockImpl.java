package org.touchhome.app.workspace;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.BroadcastLock;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Log4j2
public class BroadcastLockImpl<T> implements BroadcastLock<T> {
    private final Condition condition;
    private final ReentrantLock lock;
    private String key;
    private Object expectedValue;
    private Map<String, Runnable> releaseListeners;
    private List<Consumer<Object>> signalListener;

    @Getter
    private T value;

    public BroadcastLockImpl(String id, Object expectedValue) {
        this.key = id;
        this.expectedValue = expectedValue;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        log.debug("Creating broadcast lock: <{}>", key);
    }

    @Override
    @SneakyThrows
    public boolean await(WorkspaceBlock workspaceBlock, int timeout, TimeUnit timeUnit) {
        try {
            log.debug("Call broadcast <{}> await", key);
            lock.lock();
            workspaceBlock.setState("wait event");
            if (timeout == 0) {
                condition.await();
            } else {
                condition.await(timeout, timeUnit);
            }
            return true;
        } catch (InterruptedException ex) {
            if (!Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception ex) {
            log.error("Unrecognized error while call broadcast await", ex);
        } finally {
            lock.unlock();
        }
        return false;
    }

    public void signalAll(T value) {
        if (expectedValue != null && !Objects.equals(expectedValue, value)) {
            return;
        }
        try {
            log.debug("Call broadcast <{}> signalAll", key);
            lock.lock();
            this.value = value;

            if (signalListener != null) {
                signalListener.forEach(l -> l.accept(value));
            }

            condition.signalAll();
        } catch (Exception ex) {
            log.error("Unrecognized error while call broadcast signalAll", ex);
        } finally {
            lock.unlock();
        }
    }

    public void addReleaseListener(String key, Runnable listener) {
        if (releaseListeners == null) {
            releaseListeners = new HashMap<>();
        }
        releaseListeners.put(key, listener);
    }

    public void release() {
        if (releaseListeners != null) {
            for (Runnable releaseListener : releaseListeners.values()) {
                releaseListener.run();
            }
        }
    }

    public void addSignalListener(Consumer<Object> valueListener) {
        if (signalListener == null) {
            signalListener = new ArrayList<>();
        }
        signalListener.add(valueListener);
    }
}

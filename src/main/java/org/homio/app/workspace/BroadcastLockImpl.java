package org.homio.app.workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.workspace.BroadcastLock;
import org.homio.api.workspace.WorkspaceBlock;

@Log4j2
public class BroadcastLockImpl implements BroadcastLock {

    private final Condition condition;
    private final ReentrantLock lock;
    private final Predicate<Object> valueCheck;
    private final String key;
    private final Object expectedValue;
    private Map<String, Runnable> releaseListeners;
    private List<Consumer<Object>> signalListener;

    @Getter
    private Object value;

    public BroadcastLockImpl(String id, Object expectedValue) {
        this.key = id;
        this.expectedValue = expectedValue;
        if (this.expectedValue == null) {
            this.valueCheck = o -> true;
        } else if (this.expectedValue instanceof Pattern) {
            this.valueCheck =
                    o -> ((Pattern) this.expectedValue).matcher(Objects.toString(value)).matches();
        } else {
            this.valueCheck = o -> Objects.equals(o, this.expectedValue);
        }
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        log.debug("Creating broadcast lock: <{}>", key);
    }

    @Override
    @SneakyThrows
    public boolean await(WorkspaceBlock workspaceBlock, int timeout, TimeUnit timeUnit) {
        try {
            lock.lock();
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

    public void signalAll(Object value) {
        if (!this.valueCheck.test(value)) {
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

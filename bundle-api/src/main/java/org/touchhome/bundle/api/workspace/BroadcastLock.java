package org.touchhome.bundle.api.workspace;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class BroadcastLock<T> {
    private final Condition condition;
    private final ReentrantLock lock;
    private String key;

    @Getter
    private T value;

    BroadcastLock(String id) {
        this.key = id;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        log.debug("Creating broadcast lock: <{}>", key);
    }

    @SneakyThrows
    public boolean await(WorkspaceBlock workspaceBlock) {
        try {
            log.debug("Call broadcast <{}> await", key);
            lock.lock();
            workspaceBlock.setState("wait event");
            condition.await();
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

    public void signalAll() {
        signalAll(null);
    }

    public void signalAll(T value) {
        try {
            log.debug("Call broadcast <{}> signalAll", key);
            lock.lock();
            this.value = value;
            condition.signalAll();
        } catch (Exception ex) {
            log.error("Unrecognized error while call broadcast signalAll", ex);
        } finally {
            lock.unlock();
        }
    }
}

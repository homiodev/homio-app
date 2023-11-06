package org.homio.addon.firmata.provider.command;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;

@Log4j2
class LockManager<T> {

  private Map<String, LockContext<T>> lockContextMap = new HashMap<>();
  private Map<String, Pair<T, Long>> pendingLockValues = new HashMap<>();

  public void signalAll(String key, T value) {
    if (lockContextMap.containsKey(key)) {
      lockContextMap.get(key).signalAll(value);
    } else {
      pendingLockValues.put(key, Pair.of(value, System.currentTimeMillis()));
    }
  }

  public T await(String key, int time, Runnable beforeWaitRunnable) {
    Pair<T, Long> pair = pendingLockValues.get(key);
    if (pair != null) {
      if (System.currentTimeMillis() - pair.getSecond() > 2000) {
        pendingLockValues.remove(key);
      } else {
        return pendingLockValues.remove(key).getFirst();
      }
    }
    LockContext<T> lockContext = lockContextMap.get(key);
    if (lockContext == null) {
      lockContext = new LockContext<>();
      lockContextMap.put(key, lockContext);
    }
    return lockContext.await(time, beforeWaitRunnable);
  }

  private static class LockContext<T> {

    private final Condition condition;
    private final ReentrantLock lock;
    private T value;

    public LockContext() {
      this.lock = new ReentrantLock();
      this.condition = lock.newCondition();
    }

    public void signalAll(T value) {
      try {
        lock.lock();
        this.value = value;
        condition.signalAll();
      } catch (Exception ex) {
        log.error("Unrecognized error while call lock signalAll", ex);
      } finally {
        lock.unlock();
      }
    }

    public T await(int time, Runnable beforeWaitRunnable) {
      try {
        lock.lock();
        if (beforeWaitRunnable != null) {
          beforeWaitRunnable.run();
        }
        condition.await(time, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ex) {
        if (!Thread.currentThread().isInterrupted()) {
          Thread.currentThread().interrupt();
        }
      } catch (Exception ex) {
        log.error("No signal found for lock message");
      } finally {
        lock.unlock();
      }
      return value;
    }
  }
}

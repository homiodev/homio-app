package org.homio.app.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class CollectionUtils {

  /**
   * Set which skip adding null values, and replace exiting values
   */
  public static <T> Set<T> nullSafeSet() {
    return new HashSet<T>() {
      @Override
      public boolean add(T t) {
        if (t == null) {
          return false;
        }
        super.remove(t);
        return super.add(t);
      }

      @Override
      public boolean addAll(Collection<? extends T> c) {
        return c != null && super.addAll(c);
      }
    };
  }

  public static class LastBytesBuffer {
    private byte[] buffer;
    private int size;
    private int head;

    public LastBytesBuffer(int size) {
      this.buffer = new byte[size];
      this.size = size;
      this.head = 0;
    }

    public synchronized void append(byte[] data) {
      for (byte b : data) {
        buffer[head] = b;
        head = (head + 1) % size;
      }
    }

    public synchronized byte[] getLastBytes() {
      byte[] lastBytes = new byte[size];
      int index = 0;
      for (int i = head; i != (head == 0 ? size - 1 : head - 1); i = (i == 0 ? size - 1 : i - 1)) {
        lastBytes[index++] = buffer[i];
      }
      return lastBytes;
    }

    public synchronized byte[] getActualData() {
      int actualSize = Math.min(size, head);
      byte[] actualData = new byte[actualSize];
      for (int i = 0; i < actualSize; i++) {
        actualData[i] = buffer[(head - actualSize + i + size) % size];
      }
      return actualData;
    }
  }
}

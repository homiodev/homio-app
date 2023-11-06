package org.homio.addon.firmata.provider.util;

import java.nio.ByteBuffer;

public final class THUtil {

  public static byte getByte(ByteBuffer byteBuffer) {
    byte value = byteBuffer.get();
    byteBuffer.get();
    return value;
  }

  public static short getShort(ByteBuffer payload) {
    byte low = getByte(payload);
    byte high = getByte(payload);
    return (short) ((high << 8) | (low & 0xff));
  }

  public static Long getLong(ByteBuffer payload) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    for (int i = 7; i >= 0; i--) {
      buffer.put(i, getByte(payload));
    }
    return buffer.getLong(0);
  }

  public static String getString(ByteBuffer payload, Integer capacity) {
    int length = capacity == null ? payload.remaining() / 2 : capacity;
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      byte cByte = getByte(payload);
      if (cByte != 0) {
        sb.append((char) cByte);
      }
    }
    return sb.toString();
  }
}

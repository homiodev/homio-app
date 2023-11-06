package org.homio.addon.firmata.provider.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class OneWireUtils {

  public static final byte ONEWIRE_RESET_REQUEST_BIT = 0x01;
  public static final byte ONEWIRE_SKIP_REQUEST_BIT = 0x02;
  public static final byte ONEWIRE_SELECT_REQUEST_BIT = 0x04;
  public static final byte ONEWIRE_READ_REQUEST_BIT = 0x08;
  public static final byte ONEWIRE_DELAY_REQUEST_BIT = 0x10;
  public static final byte ONEWIRE_WRITE_REQUEST_BIT = 0x20;

  public static final byte ONEWIRE_SEARCH_REQUEST = 0x40;
  public static final byte ONEWIRE_CONFIG_REQUEST = 0x41;
  public static final byte ONEWIRE_SEARCH_REPLY = 0x42;
  public static final byte ONEWIRE_READ_REPLY = 0x43;
  public static final byte ONEWIRE_SEARCH_ALARMS_REQUEST = 0x44;
  public static final byte ONEWIRE_SEARCH_ALARMS_REPLY = 0x45;

  public static final byte ONEWIRE_WITHDATA_REQUEST_BITS = 0x3C;

  public static List<OneWireDevice> readDevices(ByteBuffer payload) {
    byte[] deviceBytes = OneWireUtils.from7BitArray(payload);
    List<OneWireDevice> devices = new ArrayList<>();

    for (int i = 0; i < deviceBytes.length; i += 8) {
      if (deviceBytes.length >= i + 8) {
        byte[] device = new byte[8];
        System.arraycopy(deviceBytes, i, device, 0, 8);
        devices.add(new OneWireDevice(device));
      }
    }
    return devices;
  }

  public static byte crc8(byte[] data) {
    int crc = 0;
    int inbyte;
    int mix;
    for (int i = 0; i < data.length - 1; i++) {
      inbyte = data[i];
      for (int n = 8; n > 0; n--) {
        mix = (crc ^ inbyte) & 0x01;
        crc >>= 1;
        if (mix != 0) {
          crc ^= 0x8C;
        }
        inbyte >>= 1;
      }
    }

    return (byte) crc;
  }

  public static byte[] from7BitArray(ByteBuffer payload) {
    byte[] encoded = new byte[payload.remaining()];
    payload.get(encoded);

    int expectedBytes = encoded.length * 7 >> 3;
    ByteBuffer buffer = ByteBuffer.allocate(expectedBytes);
    for (int i = 0; i < expectedBytes; i++) {
      int j = i << 3;
      int pos = (j / 7) >>> 0;
      int shift = j % 7;
      buffer.put((byte) ((encoded[pos] >> shift) | ((encoded[pos + 1] << (7 - shift)) & 0xFF)));
    }
    return buffer.array();
  }

  public static byte[] to7BitArray(byte[] data) {
    int shift = 0;
    int previous = 0;
    ByteBuffer output = ByteBuffer.allocate(data.length * 2);
    for (byte dataByte : data) {
      int datum = dataByte & 0xff; // convert to avoid negative numbers

      if (shift == 0) {
        output.put((byte) (datum & 0x7f));
        shift++;
        previous = datum >> 7;
      } else {
        output.put((byte) (((datum << shift) & 0x7f) | previous));
        if (shift == 6) {
          output.put((byte) (datum >> 1));
          shift = 0;
        } else {
          shift++;
          previous = datum >> (8 - shift);
        }
      }
    }

    if (shift > 0) {
      output.put((byte) previous);
    }
    byte[] result = new byte[output.position()];
    System.arraycopy(output.array(), 0, result, 0, result.length);

    return result;
  }
}

package org.homio.addon.firmata.provider.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;

@Getter
public class OneWireDevice {

  byte[] device;
  long address;
  boolean validCrc;

  public OneWireDevice(byte[] device) {
    this.device = device;
    this.validCrc = OneWireUtils.crc8(device) == device[7];
    this.address = ByteBuffer.wrap(device).getLong(0);
  }

  public static ByteBuffer toByteArray(long address) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).putLong(address);
    ((Buffer) buffer).position(0);
    return buffer;
  }

  @Override
  public String toString() {
    return Hex.encodeHexString(this.device, false).replaceAll("..", "$0 ")
        + " / CRC " + (validCrc ? "valid" : "invalid");
  }

  public boolean isFamily(int family) {
    return this.device[0] == family;
  }
}

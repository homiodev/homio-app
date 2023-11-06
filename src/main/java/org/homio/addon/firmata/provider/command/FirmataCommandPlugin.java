package org.homio.addon.firmata.provider.command;

import java.nio.ByteBuffer;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.IODeviceWrapper;

public interface FirmataCommandPlugin {

  FirmataCommand getCommand();

  default String getName() {
    return getCommand().name();
  }

  default void handle(IODeviceWrapper device, FirmataBaseEntity entity, byte messageID, ByteBuffer payload) {
    throw new IllegalStateException("onRemoteExecuted not implemented for command" + getName());
  }

  default void broadcastHandle(IODeviceWrapper device, FirmataBaseEntity entity, byte messageID, short target, ByteBuffer payload) {
    throw new IllegalStateException("onRemoteExecuted not implemented for command" + getName());
  }

  default boolean isHandleBroadcastEvents() {
    return false;
  }

  default boolean hasTH() {
    return false;
  }
}

package org.homio.addon.firmata.provider.command;

import static org.homio.addon.firmata.provider.command.FirmataCommand.ONEWIRE_DATA;

import java.nio.ByteBuffer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.IODeviceWrapper;
import org.homio.addon.firmata.provider.util.OneWireDevice;
import org.homio.addon.firmata.provider.util.OneWireUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class FirmataOneWireResponseDataCommand implements FirmataCommandPlugin {

  private static final String EVENT = "1-wire-%d-%d";
  private final LockManager<Object> lockManager = new LockManager<>();

  @Override
  public FirmataCommand getCommand() {
    return ONEWIRE_DATA;
  }

  public byte[] waitForValue(Integer correlationId, Runnable beforeWaitRunnable) {
    return (byte[]) lockManager.await("1-wire-rp-" + correlationId, 5000, beforeWaitRunnable);
  }

  public List<OneWireDevice> waitForDevices(byte subCommandID, byte pin, Runnable beforeWaitRunnable) {
    return (List<OneWireDevice>) lockManager.await(String.format(EVENT, subCommandID, pin), 5000, beforeWaitRunnable);
  }

  @Override
  public void handle(IODeviceWrapper device, FirmataBaseEntity entity, byte messageID, ByteBuffer payload) {
    byte type = payload.get();
    byte pin = payload.get();
    switch (type) {
      case OneWireUtils.ONEWIRE_SEARCH_REPLY:
      case OneWireUtils.ONEWIRE_SEARCH_ALARMS_REPLY:
        this.lockManager.signalAll(String.format(EVENT, type, pin), OneWireUtils.readDevices(payload));
        break;
      case OneWireUtils.ONEWIRE_READ_REPLY:
        byte[] decoded = OneWireUtils.from7BitArray(payload);
        int correlationId = ((decoded[1] << 8) | decoded[0]) & 0xFF;

        byte[] array = new byte[decoded.length - 2];
        System.arraycopy(decoded, 2, array, 0, array.length);
        this.lockManager.signalAll("1-wire-rp-" + correlationId, array);
        break;
      default:
        log.error("Unable to find onewire type: " + type);
    }
  }

  @Override
  public boolean isHandleBroadcastEvents() {
    return true;
  }
}

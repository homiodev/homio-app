package org.homio.addon.firmata.provider;

import static org.firmata4j.firmata.parser.FirmataToken.END_SYSEX;
import static org.firmata4j.firmata.parser.FirmataToken.ONEWIRE_DATA;
import static org.firmata4j.firmata.parser.FirmataToken.START_SYSEX;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_CONFIG_REQUEST;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_DELAY_REQUEST_BIT;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_READ_REQUEST_BIT;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_RESET_REQUEST_BIT;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_SEARCH_ALARMS_REQUEST;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_SEARCH_REPLY;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_SEARCH_REQUEST;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_SELECT_REQUEST_BIT;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_WITHDATA_REQUEST_BITS;
import static org.homio.addon.firmata.provider.util.OneWireUtils.ONEWIRE_WRITE_REQUEST_BIT;
import static org.homio.addon.firmata.provider.util.OneWireUtils.to7BitArray;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.firmata4j.IODevice;
import org.homio.addon.firmata.provider.command.FirmataCommand;
import org.homio.addon.firmata.provider.util.OneWireDevice;

@Log4j2
@RequiredArgsConstructor
public class IODeviceWrapper {

  @Getter
  private final IODevice ioDevice;
  @Getter
  private final IOOneWire ioOneWire = new IOOneWire();
  private final FirmataDeviceCommunicator firmataDeviceCommunicator;
  private byte messageID;

  public byte nextMessageId() {
    if (messageID > 125) {
      messageID = 0;
    }
    messageID++;
    return messageID;
  }

  public byte sendMessage(FirmataCommand command) {
    return sendMessage(command, 0, byteBuffer -> {
    });
  }

  @SneakyThrows
  public byte sendMessage(FirmataCommand command, long longValue) {
    return sendMessage(command, Long.BYTES, payload -> payload.putLong(longValue));
  }

  public long generateUniqueIDOnRegistrationSuccess() {
    return firmataDeviceCommunicator.generateUniqueIDOnRegistrationSuccess();
  }

  @SneakyThrows
  private byte sendMessage(FirmataCommand command, int length, Consumer<ByteBuffer> consumer) {
    ByteBuffer payload = ByteBuffer.allocate(6 + length);
    payload.put(START_SYSEX);
    payload.put(command.getValue());
    byte id = nextMessageId();
    payload.put(id);
    payload.putShort(firmataDeviceCommunicator.getEntity().getTarget());
    consumer.accept(payload);
    payload.put(END_SYSEX);

    ioDevice.sendMessage(payload.array());
    return id;
  }

  @SneakyThrows
  public void sendMessage(byte[] bytes) {
    this.ioDevice.sendMessage(bytes);
  }

  public class IOOneWire {

    /**
     * Configure the passed pin as the controller in a 1-wire bus. Pass as enableParasiticPower true if you want the data pin to power the bus.
     */
    @SneakyThrows
    public void sendOneWireConfig(byte pin, boolean enableParasiticPower) {
      sendRaw(ONEWIRE_DATA, ONEWIRE_CONFIG_REQUEST, pin, (byte) (enableParasiticPower ? 0x01 : 0x00));
    }

    /**
     * Reads data from a device on the bus and invokes the passed callback.
     */
    public int sendOneWireRead(byte pin, ByteBuffer address, byte numBytesToRead) {
      Integer correlationId = (int) Math.floor(Math.random() * 255);
      log.info("WRITE correlationId: {}", correlationId);
      sendOneWireRequest(pin, ONEWIRE_READ_REQUEST_BIT, address, numBytesToRead, correlationId, null, null);
      return correlationId;
    }

    /**
     * Tells firmata to not do anything for the passed amount of ms.  For when you need to give a device attached to the bus time to do a calculation.
     */
    public void sendOneWireDelay(byte pin, int delay) {
      sendOneWireRequest(pin, ONEWIRE_DELAY_REQUEST_BIT, null, null, null, delay, null);
    }

    /**
     * Resets all devices on the bus.
     */
    public void sendOneWireReset(byte pin) {
      sendOneWireRequest(pin, ONEWIRE_RESET_REQUEST_BIT, null, null, null, null, null);
    }

    /**
     * Writes data to the bus to be received by the passed device.  The device should be obtained from a previous call to sendOneWireSearch.
     */
    public void sendOneWireWrite(byte pin, ByteBuffer address, ByteBuffer data, Integer delay, boolean reset) {
      byte subCommand = (byte) (ONEWIRE_WRITE_REQUEST_BIT | ONEWIRE_SELECT_REQUEST_BIT
          | (delay == null ? 0 : ONEWIRE_DELAY_REQUEST_BIT) | (reset ? ONEWIRE_RESET_REQUEST_BIT : 0));
      sendOneWireRequest(pin, subCommand, address, null, null, delay, data);
    }

    /**
     * Sends the passed data to the passed device on the bus, reads the specified number of bytes and invokes the passed callback.
     */
    public byte[] sendOneWireWriteAndRead(byte pin, ByteBuffer address, ByteBuffer data, byte numBytesToRead, Integer delay, boolean reset) {
      Integer correlationId = (int) Math.floor(Math.random() * 255);
      byte subCommand = (byte) (ONEWIRE_WRITE_REQUEST_BIT | ONEWIRE_READ_REQUEST_BIT | ONEWIRE_SELECT_REQUEST_BIT
          | (delay == null ? 0 : ONEWIRE_DELAY_REQUEST_BIT) | (reset ? ONEWIRE_RESET_REQUEST_BIT : 0));
      return firmataDeviceCommunicator.getOneWireCommand().waitForValue(correlationId,
          () -> sendOneWireRequest(pin, subCommand, address, numBytesToRead, correlationId, delay, data));
    }

    /**
     * Searches for 1-wire devices on the bus.  The passed callback should accept and error argument and an array of device identifiers.
     */
    public List<OneWireDevice> sendOneWireSearch(byte pin) {
      return firmataDeviceCommunicator.getOneWireCommand().waitForDevices(ONEWIRE_SEARCH_REPLY, pin,
          () -> sendOneWireSearch(ONEWIRE_SEARCH_REQUEST, pin));
    }

    /**
     * Searches for 1-wire devices on the bus in an alarmed state.  The passed callback should accept and error argument and an array of device identifiers.
     */
    public List<OneWireDevice> sendOneWireAlarmsSearch(byte pin) {
      return firmataDeviceCommunicator.getOneWireCommand().waitForDevices(ONEWIRE_SEARCH_REPLY, pin,
          () -> sendOneWireSearch(ONEWIRE_SEARCH_ALARMS_REQUEST, pin));
    }

    @SneakyThrows
    public void sendOneWireSearch(byte type, byte pin) {
      sendRaw(ONEWIRE_DATA, type, pin);
    }

    /**
     * Searches for 1-wire devices on the bus.  The passed callback should accept and error argument and an array of device identifiers.
     */
    @SneakyThrows
    private void sendOneWireRequest(byte pin, Byte subcommand, ByteBuffer address, Byte numBytesToRead,
        Integer correlationId, Integer delay, ByteBuffer dataToWrite) {
      byte[] data = dataToWrite == null ? new byte[0] : dataToWrite.array();
      int length = (address == null ? 0 : 8) + (numBytesToRead == null ? 0 : 2) + (correlationId == null ? 0 : 2) + (delay == null ? 0 : 4);
      ByteBuffer bytes = ByteBuffer.allocate(length + data.length);

      if (address != null || numBytesToRead != null || correlationId != null || delay != null || dataToWrite != null) {
        subcommand = subcommand == null ? ONEWIRE_WITHDATA_REQUEST_BITS : subcommand;
      }

      if (address != null) {
        bytes.put(address.array());
      }

      if (numBytesToRead != null) {
        bytes.put((byte) (numBytesToRead & 0xFF));
        bytes.put((byte) ((numBytesToRead >> 8) & 0xFF));
      }

      if (correlationId != null) {
        bytes.put((byte) (correlationId & 0xFF));
        bytes.put((byte) ((correlationId >> 8) & 0xFF));
      }

      if (delay != null) {
        bytes.put((byte) (delay & 0xFF));
        bytes.put((byte) ((delay >> 8) & 0xFF));
        bytes.put((byte) ((delay >> 16) & 0xFF));
        bytes.put((byte) ((delay >> 24) & 0xFF));
      }
      for (int i = 0; i < data.length; i++) {
        bytes.put(data[i]);
      }

      sendRaw(ONEWIRE_DATA, subcommand, pin, to7BitArray(bytes.array()));
    }

    private void sendRaw(byte command, byte subcommand, byte pin) {
      sendRaw(command, subcommand, pin, new byte[0]);
    }

    private void sendRaw(byte command, byte subcommand, byte pin, byte data) {
      sendRaw(command, subcommand, pin, new byte[]{data});
    }

    @SneakyThrows
    private void sendRaw(byte command, byte subcommand, byte pin, byte[] data) {
      ByteBuffer payload = ByteBuffer.allocate(5 + data.length);
      payload.put(START_SYSEX);
      payload.put(command);
      payload.put(subcommand);
      payload.put(pin);
      payload.put(data);
      payload.put(END_SYSEX);
      ioDevice.sendMessage(payload.array());
      // log.info("Send raw: " + getString(payload.array()).replaceAll("..", "$0 "));
    }

        /*public String getString(byte[] b) throws Exception {
            String result = "";
            for (int i = 0; i < b.length; i++) {
                result +=
                        Integer.toString((b[i] & 0xff) + 0x100, 10).substring(1);
            }
            return result;
        }*/
  }
}

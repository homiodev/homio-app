package org.homio.addon.firmata.workspace;

import java.nio.ByteBuffer;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.firmata4j.Pin;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.addon.firmata.FirmataBundleEntrypoint;
import org.homio.addon.firmata.provider.util.OneWireDevice;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3FirmataOneWireBlocks extends Scratch3FirmataBaseBlock {

  public static final String ONE_REST = "rest/firmata/onewire/address?family=";
  private final MenuBlock.ServerMenuBlock pinMenu1Wire;
  private final MenuBlock.ServerMenuBlock menuTemperatureAddress;

  private final Scratch3Block ds18b20Value;
  boolean sendConfig = false;

  public Scratch3FirmataOneWireBlocks(Context context, FirmataBundleEntrypoint firmataBundleEntrypoint) {
    super("#83A044", context, firmataBundleEntrypoint, "onewire");

    this.pinMenu1Wire = menuServer(PIN, REST_PIN + Pin.Mode.ONEWIRE, "1-Wire").setDependency(firmataIdMenu);
    this.menuTemperatureAddress = menuServer("pinMenu1WireAddress",
        ONE_REST + ONE_WIRE.DS18B20.TEMPERATURE_FAMILY, "Temperature address")
        .setDependency(firmataIdMenu, this.pinMenu1Wire);

    this.ds18b20Value = blockReporter(10, "DS18B20",
        "DS18B20(1-Wire) on [PIN] address [ADDRESS] of [FIRMATA]", this::getDS18B20Value, block -> {
          addPinMenu(block, this.pinMenu1Wire, null);
        });
    this.ds18b20Value.addArgument("ADDRESS", this.menuTemperatureAddress);
  }

  private State getDS18B20Value(WorkspaceBlock workspaceBlock) {
    Long longAddress = workspaceBlock.getMenuValue("ADDRESS", this.menuTemperatureAddress, Long.class);

    ByteBuffer address = OneWireDevice.toByteArray(longAddress);
    return execute(workspaceBlock, false, this.pinMenu1Wire, (entity, pin) -> {
      entity.getDevice().getIoOneWire().sendOneWireConfig(pin.getIndex(), true);

      // start conversion, with parasite power on at the end
      ByteBuffer payload = ByteBuffer.allocate(1).put(ONE_WIRE.DS18B20.CONVERT_TEMPERATURE_COMMAND);
      entity.getDevice().getIoOneWire().sendOneWireWrite(pin.getIndex(), address, payload, null, true);

      // maybe 750ms is enough, maybe not
      entity.getDevice().getIoOneWire().sendOneWireDelay(pin.getIndex(), 1);

      // Read Scratchpad
      payload = ByteBuffer.allocate(1).put(ONE_WIRE.DS18B20.READ_SCRATCHPAD_COMMAND);
      byte[] data = entity.getDevice().getIoOneWire()
          .sendOneWireWriteAndRead(pin.getIndex(), address, payload, ONE_WIRE.DS18B20.READ_COUNT, null, true);
      float value = (float) (data == null ? -1 : ((data[1] & 0xFF) << 8) | data[0] & 0xFF) / 16;
      return new DecimalType(value);
    });
  }

  private static class ONE_WIRE {

    private static class DS18B20 {

      private static final byte TEMPERATURE_FAMILY = 0x28;
      private static final byte CONVERT_TEMPERATURE_COMMAND = 0x44;
      private static final byte READ_SCRATCHPAD_COMMAND = (byte) 0xBE;
      private static final byte READ_COUNT = 2;
    }
  }
}

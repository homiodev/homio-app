package org.homio.addon.firmata;

import static org.homio.addon.firmata.workspace.Scratch3FirmataBaseBlock.PIN;
import static org.homio.addon.firmata.workspace.Scratch3FirmataBlocks.FIRMATA_ID_MENU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.firmata4j.Pin;
import org.homio.api.Context;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.util.OneWireDevice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/firmata")
@RequiredArgsConstructor
public class FirmataController {

  private final Context context;

  @GetMapping("/onewire/address")
  public Collection<OptionModel> getOneWireAddress(
      @RequestParam(name = "family", required = false) Byte family,
      @RequestParam(name = FIRMATA_ID_MENU, required = false) String firmataIdMenu,
      @RequestParam(name = PIN, required = false) String pin) {
    if (firmataIdMenu != null && pin != null) {
      byte pinNum;
      try {
        pinNum = Byte.parseByte(pin);
      } catch (Exception ex) {
        return Collections.emptyList();
      }
      FirmataBaseEntity entity = context.db().getEntity(firmataIdMenu);
      if (entity != null && entity.getJoined() == Status.ONLINE) {
        entity.getDevice().getIoOneWire().sendOneWireConfig(pinNum, true);

        List<OneWireDevice> devices = entity.getDevice().getIoOneWire().sendOneWireSearch(pinNum);
        if (devices != null) {
          List<OneWireDevice> ds18B20Devices = devices;
          if (family != null) {
            ds18B20Devices = devices.stream().filter(d -> d.isFamily(0x28)).collect(Collectors.toList());
          }
          return ds18B20Devices.stream().map(d -> OptionModel.of(String.valueOf(d.getAddress()), d.toString())).collect(Collectors.toList());
        }
      }
    }
    return Collections.emptyList();
  }

  @GetMapping("/pin")
  public Collection<OptionModel> getAllPins(@RequestParam(name = FIRMATA_ID_MENU, required = false) String firmataIdMenu) {
    return this.getPins(null, firmataIdMenu);
  }

  @GetMapping("pin/{mode}")
  public Collection<OptionModel> getPins(@PathVariable("mode") String mode, @RequestParam(name = FIRMATA_ID_MENU, required = false) String firmataIdMenu) {
    if (firmataIdMenu != null) {
      FirmataBaseEntity entity = context.db().getEntity(firmataIdMenu);
      if (entity != null && entity.getJoined() == Status.ONLINE) {
        Pin.Mode supportMode = mode == null ? null : Pin.Mode.valueOf(mode);
        List<OptionModel> pins = new ArrayList<>();
        ArrayList<Pin> sortedPins = new ArrayList<>(entity.getDevice().getIoDevice().getPins());
        sortedPins.sort(Comparator.comparingInt(Pin::getIndex));
        for (Pin pin : sortedPins) {
          if (!pin.getSupportedModes().isEmpty() && (supportMode == null || pin.getSupportedModes().contains(supportMode))) {
            String name = String.format("%2d / %s", pin.getIndex(), pin.getMode());
            pins.add(OptionModel.of(String.valueOf(pin.getIndex()), name));
          }
        }
        return pins;
      }
    }
    return Collections.emptyList();
  }
}

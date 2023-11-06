package org.homio.addon.firmata;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.homio.addon.firmata.provider.command.FirmataCommand.SYSEX_PING;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.model.FirmataNetworkEntity;
import org.homio.addon.firmata.provider.FirmataCommandPlugins;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.model.Status;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class FirmataBundleEntrypoint implements AddonEntrypoint {

  @Getter
  private static final Map<String, UdpPayload> udpFoundDevices = new HashMap<>();

  private final Context context;
  private final ArduinoConsolePlugin arduinoConsolePlugin;

  @Getter
  private final FirmataCommandPlugins firmataCommandPlugins;

  // this method fires only from devices that support internet access
  public static boolean foundController(Context context, String board, String deviceID, String hostAddress, String headerConfirmItemsKey) {
    // check if we already have firmata device with deviceID
    FirmataBaseEntity<?> device;
    if (deviceID != null) {
      device = context.db().findAll(FirmataBaseEntity.class).stream()
          .filter(d -> Objects.equals(d.getIeeeAddress(), deviceID))
          .findAny().orElse(null);
    } else {
      device = context.db().findAll(FirmataNetworkEntity.class).stream()
          .filter(d -> Objects.equals(d.getIp(), hostAddress))
          .findAny().orElse(null);
    }

    if (device != null) {
      if (device instanceof FirmataNetworkEntity ae) {
        if (!hostAddress.equals(ae.getIp())) {
          context.ui().toastr().warn("FIRMATA.EVENT.CHANGED_IP",
              FlowMap.of("DEVICE", device.getTitle(), "OLD", ae.getIp(), "NEW", hostAddress));
          // update device ip address and try restart it
          context.db().save(ae.setIp(hostAddress)).restartCommunicator();
        } else {
          log.info("Firmata device <{}> up to date.", device.getTitle());
        }
      } else {
        context.ui().toastr().warn("FIRMATA.EVENT.FIRMATA_WRONG_DEVICE_TYPE", FlowMap.of("ID",
            deviceID, "NAME", device.getTitle()));
      }
      return false;
    } else {
      List<String> messages = new ArrayList<>();
      messages.add(Lang.getServerMessage("FIRMATA.NEW_DEVICE_QUESTION"));
      if (board != null) {
        messages.add(Lang.getServerMessage("FIRMATA.NEW_DEVICE_BOARD", "BOARD", board));
      }
      if (deviceID != null) {
        messages.add(Lang.getServerMessage("FIRMATA.NEW_DEVICE_ID", "DEVICE_ID", deviceID));
      }
      messages.add(Lang.getServerMessage("FIRMATA.NEW_DEVICE_ADDRESS", "ADDRESS", hostAddress));

      context.ui().dialog().sendConfirmation("Confirm-Firmata-" + deviceID, "TITLE.NEW_DEVICE", () -> {
        // save device and try restart it
        context.db().save(new FirmataNetworkEntity().setIp(hostAddress)).restartCommunicator();
      }, messages, headerConfirmItemsKey);
      context.ui().toastr().info("FIRMATA.EVENT.FOUND_FIRMATA_DEVICE",
          FlowMap.of("ID", defaultString(deviceID, "-"), "IP", hostAddress,
              "BOARD", defaultString(board, "-")));
      udpFoundDevices.put(hostAddress,
          new UdpPayload(hostAddress, deviceID == null ? null : Short.parseShort(deviceID), board));
      return true;
    }
  }

  public void init() {
    arduinoConsolePlugin.init();
    restartFirmataProviders();
    this.context.event().addEntityUpdateListener(FirmataBaseEntity.class, "firmata-restart-comm-listen", FirmataBaseEntity::restartCommunicator);

    this.context.hardware().network().listenUdp("listen-firmata-udp", null, 8266, (datagramPacket, payload) -> {
      if (payload.startsWith("th:")) {
        String[] parts = payload.split(":");
        if (parts.length == 3) {
          foundController(context, parts[1].trim(), parts[2].trim(), datagramPacket.getAddress().getHostAddress(), null);
          return;
        }
      }
      log.warn("Got udp notification on port 8266 with unknown payload: <{}>", payload);
    });

    // ping firmata device if live status is online
    this.context.bgp().builder("firmata-device-ping").interval(Duration.ofMinutes(3)).execute(() -> {
      for (FirmataBaseEntity<?> firmataBaseEntity : context.db().findAll(FirmataBaseEntity.class)) {
        if (firmataBaseEntity.getJoined() == Status.ONLINE) {
          log.debug("Ping firmata device: <{}>", firmataBaseEntity.getTitle());
          firmataBaseEntity.getDevice().sendMessage(SYSEX_PING);
        }
      }
    });
  }

  private void restartFirmataProviders() {
    this.context.db().findAll(FirmataBaseEntity.class).forEach(FirmataBaseEntity::restartCommunicator);
  }

  @Getter
  @AllArgsConstructor
  public static class UdpPayload {

    private final String address;
    private final Short deviceID;
    private final String board;

    @Override
    public String toString() {
      return String.format("IP: %s. [Device ID: %s / Board: %s]", address, deviceID, board);
    }
  }
}

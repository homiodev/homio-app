package org.homio.addon.firmata.provider;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.homio.api.Context;
import org.homio.addon.firmata.provider.command.FirmataCommandPlugin;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirmataCommandPlugins {

  private final Context context;

  private Map<Byte, FirmataCommandPlugin> firmataCommandPlugins;

  // deferred initialization
  private Map<Byte, FirmataCommandPlugin> getFirmataCommandPlugins() {
    if (firmataCommandPlugins == null) {
      this.firmataCommandPlugins = context.getBeansOfType(FirmataCommandPlugin.class).stream().collect(Collectors.toMap(p -> p.getCommand().getValue(), p -> p));
    }
    return firmataCommandPlugins;
  }

  // for 3-th parts
  public void addFirmataCommandPlugin(FirmataCommandPlugin firmataCommandPlugin) {
    firmataCommandPlugins.putIfAbsent(firmataCommandPlugin.getCommand().getValue(), firmataCommandPlugin);
  }

  public FirmataCommandPlugin getFirmataCommandPlugin(byte commandID) {
    FirmataCommandPlugin commandPlugin = getFirmataCommandPlugins().get(commandID);
    if (commandPlugin == null) {
      throw new IllegalArgumentException("Unable to find RF24CommandPlugin for commandID: " + commandID);
    }
    return commandPlugin;
  }
}

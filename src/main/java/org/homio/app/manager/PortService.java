package org.homio.app.manager;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.state.OnOffType;
import org.homio.api.ui.UI.Color;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port manager scan all setting addons to match type as SerialPort and listen if ports available or not, fire notifications and set header notifications
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PortService implements ContextCreated, ContextRefreshed {

  private final ContextImpl context;

  private final Map<Class<? extends SettingPlugin<SerialPort>>, Pair<String, Boolean>>
    portListeners = new HashMap<>();
  private Set<String> ports = new HashSet<>();
  private List<SettingPluginOptions<SerialPort>> portSettingPlugins;

  @Override
  public void onContextRefresh(Context context) {
    listenPortAvailability();
  }

  @Override
  public void onContextCreated(ContextImpl context) {
    listenPortAvailability();
    Duration checkPortInterval = context.setting().getEnvRequire("interval-port-test", Duration.class, Duration.ofSeconds(10), true);
    this.context
      .bgp()
      .builder("check-port")
      .cancelOnError(false)
      .interval(checkPortInterval)
      .execute(this::checkPortsAvailability);
  }

  public void listenPortAvailability() {
    this.portListeners.clear();
    this.portSettingPlugins = ContextSettingImpl
      .settingPluginsBy(sp -> SerialPort.class.equals(sp.getType()))
      .stream()
      .map(sp -> (SettingPluginOptions<SerialPort>) sp)
      .collect(Collectors.toList());

    for (SettingPlugin<SerialPort> portSettingPlugin : portSettingPlugins) {
      Class<? extends SettingPlugin<SerialPort>> clazz =
        (Class<? extends SettingPlugin<SerialPort>>) portSettingPlugin.getClass();
      String portRawValue = context.setting().getRawValue(clazz);
      if (StringUtils.isNotEmpty(portRawValue)) {
        addPortToListening(clazz, portRawValue, context.setting().getValue(clazz));
      }
      context.setting().listenValue(clazz,
        "pm-listen-user-changes", serialPort -> {
          String newPortRawValue = context.setting().getRawValue(clazz);
          if (StringUtils.isEmpty(newPortRawValue)) {
            portListeners.remove(clazz);
            portAvailable(newPortRawValue);
          } else {
            addPortToListening(clazz, newPortRawValue, serialPort);
          }
        });
    }
  }

  private void addPortToListening(
    Class<? extends SettingPlugin<SerialPort>> clazz,
    String portRawValue,
    SerialPort serialPort) {
    portListeners.put(clazz, MutablePair.of(portRawValue, serialPort != null));
    if (serialPort == null) {
      addPortNotAvailableNotification(portRawValue);
    }
  }

  private void checkPortsAvailability() {
    Map<String, SerialPort> ports = new HashMap<>();
    for (SerialPort serialPort : SerialPort.getCommPorts()) {
      ports.putIfAbsent(serialPort.getSystemPortName(), serialPort);
    }
    // notify UI for reload available options
    if (!this.ports.equals(ports.keySet())) {
      // Notify that any port has been changed
      context.event().fireEvent("any-port-changed", OnOffType.ON);

      this.ports = ports.keySet();

      for (SettingPluginOptions<?> portSettingPlugin : portSettingPlugins) {
        context.setting().reloadSettings(portSettingPlugin.getClass());
      }
    }
    for (Map.Entry<Class<? extends SettingPlugin<SerialPort>>, Pair<String, Boolean>> entry :
      portListeners.entrySet()) {
      String portName = entry.getValue().getKey();
      if (ports.containsKey(portName)) {
        // check if we didn't fire event already
        if (!entry.getValue().getValue()) {
          entry.getValue().setValue(true);
          context.setting().notifyValueStateChanged(entry.getKey());
          portAvailable(portName);
        }
        // else if we store that port was accessible then set value to false
      } else if (entry.getValue().getValue()) {
        entry.getValue().setValue(false);
        context.setting().notifyValueStateChanged(entry.getKey());
      }
    }
  }

  private void portAvailable(String portName) {
    log.info("Port became available: <{}>", portName);
    context.ui().notification().updateBlock("PORTS", blockBuilder ->
      blockBuilder.removeInfo(portName));
  }

  private void addPortNotAvailableNotification(String portName) {
    log.warn("Port became not available: <{}>", portName);
    context.ui().notification().addOrUpdateBlock("PORTS", "PORTS", new Icon("fab fa-usb", "#A18123"), blockBuilder ->
      blockBuilder.addInfo(portName, new Icon("fas fa-square", Color.RED),
        Lang.getServerMessage("ERROR.PORT_NOT_AVAILABLE", portName)));
  }
}

package org.homio.app.manager;

import com.fazecast.jSerialComm.SerialPort;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.springframework.stereotype.Component;

/**
 * Port manager scan all setting addons to match type as SerialPort and listen if ports available or not, fire notifications and set header notifications
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PortService implements ContextCreated, ContextRefreshed {

    private final EntityContextImpl entityContext;

    private final Map<Class<? extends SettingPlugin<SerialPort>>, Pair<String, Boolean>>
            portListeners = new HashMap<>();
    private Set<String> ports = new HashSet<>();
    private List<SettingPluginOptions<SerialPort>> portSettingPlugins;

    @Override
    public void onContextRefresh() {
        listenPortAvailability();
    }

    @Override
    public void onContextCreated(EntityContextImpl entityContext) {
        listenPortAvailability();
        Duration checkPortInterval = this.entityContext.getAppProperties().getCheckPortInterval();
        this.entityContext
                .bgp()
                .builder("check-port")
                .cancelOnError(false)
                .interval(checkPortInterval)
                .execute(this::checkPortsAvailability);
    }

    public void listenPortAvailability() {
        this.portListeners.clear();
        this.portSettingPlugins = EntityContextSettingImpl
            .settingPluginsBy(sp -> SerialPort.class.equals(sp.getType()))
            .stream()
            .map(sp -> (SettingPluginOptions<SerialPort>) sp)
            .collect(Collectors.toList());

        for (SettingPlugin<SerialPort> portSettingPlugin : portSettingPlugins) {
            Class<? extends SettingPlugin<SerialPort>> clazz =
                (Class<? extends SettingPlugin<SerialPort>>) portSettingPlugin.getClass();
            String portRawValue = entityContext.setting().getRawValue(clazz);
            if (StringUtils.isNotEmpty(portRawValue)) {
                addPortToListening(clazz, portRawValue, entityContext.setting().getValue(clazz));
            }
            entityContext.setting().listenValue(clazz,
                "pm-listen-user-changes", serialPort -> {
                    String newPortRawValue = entityContext.setting().getRawValue(clazz);
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
            entityContext.event().fireEvent("any-port-changed", true);

            this.ports = ports.keySet();

            for (SettingPluginOptions<?> portSettingPlugin : portSettingPlugins) {
                entityContext.setting().reloadSettings(portSettingPlugin.getClass());
            }
        }
        for (Map.Entry<Class<? extends SettingPlugin<SerialPort>>, Pair<String, Boolean>> entry :
                portListeners.entrySet()) {
            String portName = entry.getValue().getKey();
            if (ports.containsKey(portName)) {
                // check if we didn't fire event already
                if (!entry.getValue().getValue()) {
                    entry.getValue().setValue(true);
                    entityContext.setting().notifyValueStateChanged(entry.getKey());
                    portAvailable(portName);
                }
                // else if we store that port was accessible then set value to false
            } else if (entry.getValue().getValue()) {
                entry.getValue().setValue(false);
                entityContext.setting().notifyValueStateChanged(entry.getKey());
            }
        }
    }

    private void portAvailable(String portName) {
        log.info("Port became available: <{}>", portName);
        entityContext.ui().updateNotificationBlock("PORTS", blockBuilder ->
            blockBuilder.removeInfo("port-" + portName));
    }

    private void addPortNotAvailableNotification(String portName) {
        log.warn("Port became not available: <{}>", portName);
        entityContext.ui().addOrUpdateNotificationBlock("PORTS", "PORTS", "", "", blockBuilder ->
            blockBuilder.addInfo("port-" + portName,
                Lang.getServerMessage("ERROR.PORT_NOT_AVAILABLE", "PORT", portName), "", "fas fa-usb", "#9C8033"));
    }
}

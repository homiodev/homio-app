package org.touchhome.app.manager;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.setting.BundleSettingPluginPort.PORT_UNAVAILABLE;

/**
 * Port manager scan all setting bundles to match type as SerialPort and listen if ports available or not,
 * fire notifications and set header notifications
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PortManager {
    private final EntityContextImpl entityContext;

    private Map<Class<? extends BundleSettingPlugin<SerialPort>>, Pair<String, Boolean>> portListeners = new HashMap<>();

    public void postConstruct() {
        portListeners.clear();
        listenPortAvailability();
        entityContext.schedule("check-port", 10, TimeUnit.SECONDS, this::checkPortsAvailability, true);
    }

    public void listenPortAvailability() {
        Collection<BundleSettingPlugin> settingPlugins = EntityContextImpl.settingPluginsByPluginKey.values();
        List<BundleSettingPlugin<SerialPort>> portSettingPlugins = settingPlugins
                .stream().filter(sp -> SerialPort.class.equals(sp.getType()))
                .map(sp -> (BundleSettingPlugin<SerialPort>) sp)
                .collect(Collectors.toList());

        for (BundleSettingPlugin<SerialPort> portSettingPlugin : portSettingPlugins) {
            Class<? extends BundleSettingPlugin<SerialPort>> clazz = (Class<? extends BundleSettingPlugin<SerialPort>>) portSettingPlugin.getClass();
            String portRawValue = entityContext.getSettingRawValue(clazz);
            if (StringUtils.isNotEmpty(portRawValue)) {
                addPortToListening(clazz, portRawValue, entityContext.getSettingValue(clazz));
            }
            entityContext.listenSettingValue(clazz, "pm-listen-user-changes", serialPort -> {
                String newPortRawValue = entityContext.getSettingRawValue(clazz);
                if (StringUtils.isEmpty(newPortRawValue)) {
                    portListeners.remove(clazz);
                    portAvailable(clazz, newPortRawValue);
                } else {
                    addPortToListening(clazz, newPortRawValue, serialPort);
                }
            });
        }
    }

    private void addPortToListening(Class<? extends BundleSettingPlugin<SerialPort>> clazz, String portRawValue, SerialPort serialPort) {
        portListeners.put(clazz, MutablePair.of(portRawValue, serialPort != null));
        if (serialPort == null) {
            addPortNotAvailableNotification(clazz, portRawValue);
        }
    }

    private void checkPortsAvailability() {
        if (!portListeners.isEmpty()) {
            Map<String, SerialPort> ports = new HashMap<>();
            for (SerialPort serialPort : SerialPort.getCommPorts()) {
                ports.putIfAbsent(serialPort.getSystemPortName(), serialPort);
            }
            for (Map.Entry<Class<? extends BundleSettingPlugin<SerialPort>>, Pair<String, Boolean>> entry : portListeners.entrySet()) {
                String portName = entry.getValue().getKey();
                if (ports.containsKey(portName)) {
                    // check if we didn't fire event already
                    if (!entry.getValue().getValue()) {
                        entry.getValue().setValue(true);
                        entityContext.notifySettingValueStateChanged(entry.getKey());
                        portAvailable(entry.getKey(), portName);
                    }
                    // else if we store that port was accessible then set value to false
                } else if (entry.getValue().getValue()) {
                    entry.getValue().setValue(false);
                    entityContext.notifySettingValueStateChanged(entry.getKey());
                }
            }
        }
    }

    private void portAvailable(Class<? extends BundleSettingPlugin<SerialPort>> clazz, String portName) {
        log.info("Port became available: <{}>", portName);
        entityContext.removeHeaderNotification(NotificationEntityJSON.info(clazz.getSimpleName()));
    }

    private void addPortNotAvailableNotification(Class<? extends BundleSettingPlugin<SerialPort>> settingPluginClass, String portName) {
        log.warn("Port became not available: <{}>", portName);
        entityContext.addHeaderNotification(NotificationEntityJSON.danger(settingPluginClass.getSimpleName())
                .setName(portName)
                .setDescription(PORT_UNAVAILABLE + settingPluginClass.getSimpleName()));
    }
}

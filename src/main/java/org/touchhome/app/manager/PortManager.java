package org.touchhome.app.manager;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;

import java.util.*;
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
    private Set<String> ports = new HashSet<>();
    private List<BundleSettingPlugin<SerialPort>> portSettingPlugins;

    public void postConstruct() {
        portListeners.clear();
        listenPortAvailability();
        entityContext.bgp().schedule("check-port", 10, TimeUnit.SECONDS, this::checkPortsAvailability, true);
    }

    public void listenPortAvailability() {
        Collection<BundleSettingPlugin> settingPlugins = EntityContextSettingImpl.settingPluginsByPluginKey.values();
        this.portSettingPlugins = settingPlugins
                .stream().filter(sp -> SerialPort.class.equals(sp.getType()))
                .map(sp -> (BundleSettingPlugin<SerialPort>) sp)
                .collect(Collectors.toList());

        for (BundleSettingPlugin<SerialPort> portSettingPlugin : portSettingPlugins) {
            Class<? extends BundleSettingPlugin<SerialPort>> clazz = (Class<? extends BundleSettingPlugin<SerialPort>>) portSettingPlugin.getClass();
            String portRawValue = entityContext.setting().getRawValue(clazz);
            if (StringUtils.isNotEmpty(portRawValue)) {
                addPortToListening(clazz, portRawValue, entityContext.setting().getValue(clazz));
            }
            entityContext.setting().listenValue(clazz, "pm-listen-user-changes", serialPort -> {
                String newPortRawValue = entityContext.setting().getRawValue(clazz);
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
        Map<String, SerialPort> ports = new HashMap<>();
        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            ports.putIfAbsent(serialPort.getSystemPortName(), serialPort);
        }
        // notify UI for reload available options
        if (!this.ports.equals(ports.keySet())) {
            this.ports = ports.keySet();
            entityContext.ui().sendNotification("-settings", new JSONObject().put("reload",
                    portSettingPlugins.stream().map(SettingEntity::getKey).collect(Collectors.toSet())));
        }
        for (Map.Entry<Class<? extends BundleSettingPlugin<SerialPort>>, Pair<String, Boolean>> entry : portListeners.entrySet()) {
            String portName = entry.getValue().getKey();
            if (ports.containsKey(portName)) {
                // check if we didn't fire event already
                if (!entry.getValue().getValue()) {
                    entry.getValue().setValue(true);
                    entityContext.setting().notifyValueStateChanged(entry.getKey());
                    portAvailable(entry.getKey(), portName);
                }
                // else if we store that port was accessible then set value to false
            } else if (entry.getValue().getValue()) {
                entry.getValue().setValue(false);
                entityContext.setting().notifyValueStateChanged(entry.getKey());
            }
        }
    }

    private void portAvailable(Class<? extends BundleSettingPlugin<SerialPort>> clazz, String portName) {
        log.info("Port became available: <{}>", portName);
        entityContext.ui().removeHeaderNotification(NotificationEntityJSON.info(clazz.getSimpleName()));
    }

    private void addPortNotAvailableNotification(Class<? extends BundleSettingPlugin<SerialPort>> settingPluginClass, String portName) {
        log.warn("Port became not available: <{}>", portName);
        entityContext.ui().addHeaderNotification(NotificationEntityJSON.danger(settingPluginClass.getSimpleName())
                .setName(portName)
                .setValue(PORT_UNAVAILABLE + settingPluginClass.getSimpleName()));
    }
}

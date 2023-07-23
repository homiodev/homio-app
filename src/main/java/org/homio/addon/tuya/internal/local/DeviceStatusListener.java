package org.homio.addon.tuya.internal.local;

import java.util.Map;

/**
 * The {@link DeviceStatusListener} encapsulates device status data
 */
public interface DeviceStatusListener {
    void processDeviceStatus(Map<Integer, Object> deviceStatus);

    void connectionStatus(boolean status);
}

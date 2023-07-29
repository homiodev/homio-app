package org.homio.addon.tuya.internal.local;

import org.homio.addon.tuya.internal.local.dto.DeviceInfo;

/**
 * Interface to report new device information
 */
public interface DeviceInfoSubscriber {
    void deviceInfoChanged(DeviceInfo deviceInfo);
}

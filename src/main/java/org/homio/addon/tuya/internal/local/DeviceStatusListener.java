package org.homio.addon.tuya.internal.local;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The {@link DeviceStatusListener} encapsulates device status data
 */
public interface DeviceStatusListener {
    void processDeviceStatus(@Nullable String cid, @NotNull Map<Integer, Object> deviceStatus);

    void onDisconnected(@NotNull String message);

    void onConnected();
}

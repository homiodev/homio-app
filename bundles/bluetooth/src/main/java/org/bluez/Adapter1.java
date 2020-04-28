package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface Adapter1 extends DBusInterface {
    void StartDiscovery();

    void SetDiscoveryFilter(Map<String, Variant> properties);

    void StopDiscovery();

    void RemoveDevice(DBusInterface device);
}

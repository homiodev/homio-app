package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface GattManager1 extends DBusInterface {
    void RegisterApplication(DBusInterface application, Map<String, Variant> options);

    void UnregisterApplication(DBusInterface application);
}

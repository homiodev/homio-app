package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface Media1 extends DBusInterface {
    void RegisterEndpoint(DBusInterface endpoint, Map<String, Variant> properties);

    void UnregisterEndpoint(DBusInterface endpoint);

    void RegisterPlayer(DBusInterface player, Map<String, Variant> properties);

    void UnregisterPlayer(DBusInterface player);
}

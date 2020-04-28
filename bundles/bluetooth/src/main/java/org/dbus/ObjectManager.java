package org.dbus;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;

import java.util.Map;

@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
public interface ObjectManager extends DBusInterface {
    Map<Path, Map<String, Map<String, Variant>>> GetManagedObjects();
}

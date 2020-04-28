package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface GattApplication1 extends DBusInterface {
    Map<Path, Map<String, Map<String, Variant<?>>>> GetManagedObjects();
}

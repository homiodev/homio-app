package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface GattCharacteristic1 extends DBusInterface {
    byte[] ReadValue(Map<String, Variant> option);

    void WriteValue(byte[] value, Map<String, Variant> option);

    void StartNotify();

    void StopNotify();
}

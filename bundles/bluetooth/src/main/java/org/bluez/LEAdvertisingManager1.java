package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface LEAdvertisingManager1 extends DBusInterface {
    void RegisterAdvertisement(DBusInterface advertisement, Map<String, Variant> options);

    void UnregisterAdvertisement(DBusInterface advertisement);
}

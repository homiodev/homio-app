package org.bluez;

import java.util.Map;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

public interface LEAdvertisingManager1 extends DBusInterface {
    void RegisterAdvertisement(DBusInterface advertisement, Map<String, Variant> options);

    void UnregisterAdvertisement(DBusInterface advertisement);
}

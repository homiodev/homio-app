package org.ble;

import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;

public interface BleApplicationListener {
  void deviceConnected(Variant<String> address, InterfacesAdded signal);

  void deviceDisconnected(InterfacesRemoved signal);
}

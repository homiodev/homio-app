package org.dbus;

import lombok.Getter;
import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.Map;

@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
public interface InterfacesAddedSignal extends DBusInterface {

    @Getter
    @DBusMemberName("InterfacesAdded")
    class InterfacesAdded extends DBusSignal {
        private final Path objectPath;
        private final Map<String, Map<String, Variant>> interfacesAdded;

        public InterfacesAdded(String path, Path objectPath, Map<String, Map<String, Variant>> interfacesAdded) throws DBusException {
            super(path, objectPath, interfacesAdded);
            this.objectPath = objectPath;
            this.interfacesAdded = interfacesAdded;
        }
    }
}



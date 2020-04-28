package org.dbus;

import lombok.Getter;
import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.List;

@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
public interface InterfacesRomovedSignal extends DBusInterface {

    @Getter
    @DBusMemberName("InterfacesRemoved")
    class InterfacesRemoved extends DBusSignal {

        private final Path objectPath;
        private List<String> interfacesRemoved;

        public InterfacesRemoved(String path, Path objectPath, List<String> interfacesRemoved) throws DBusException {
            super(path, objectPath, interfacesRemoved);

            this.objectPath = objectPath;
            this.interfacesRemoved = interfacesRemoved;
        }
    }
}

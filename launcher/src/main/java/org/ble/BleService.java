package org.ble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bluez.GattService1;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

public class BleService implements GattService1, Properties {

    private static final String GATT_SERVICE_INTERFACE = "org.bluez.GattService1";
    private static final String SERVICE_UUID_PROPERTY_KEY = "UUID";
    private static final String SERVICE_PRIMARY_PROPERTY_KEY = "Primary";
    private static final String SERVICE_CHARACTERISTIC_PROPERTY_KEY = "Characteristics";

    private final String path; // APPLICATION/SERVICE

    @Getter
    private final String uuid;

    @Getter
    private List<BleCharacteristic> characteristics = new ArrayList<>();

    BleService(String path, String uuid) {
        this.path = path;
        this.uuid = uuid;
    }

    void export(DBusConnection dbusConnection) throws DBusException {
        for (BleCharacteristic characteristic : characteristics) {
            characteristic.export(dbusConnection);
        }
        dbusConnection.exportObject(this.getPath().toString(), this);
    }

    void unexport(DBusConnection dbusConnection) {
        for (BleCharacteristic characteristic : characteristics) {
            characteristic.unexport(dbusConnection);
        }
        dbusConnection.unExportObject(this.getPath().toString());
    }

    /**
     * Return the Path (dbus class)
     */
    public Path getPath() {
        return new Path(path);
    }

    /**
     * Convert the list in array[]
     */
    private Path[] getCharacteristicsPathArray() {
        Path[] pathArray = new Path[characteristics.size()];
        for (int i = 0; i < characteristics.size(); i++) {
            pathArray[i] = characteristics.get(i).getPath();
        }
        return pathArray;
    }

    public Map<String, Map<String, Variant<?>>> getProperties() {
        System.out.println("Service -> getServiceProperties");
        Map<String, Variant<?>> serviceMap = new HashMap<>();

        Variant<String> uuidProperty = new Variant<>(this.uuid);
        serviceMap.put(SERVICE_UUID_PROPERTY_KEY, uuidProperty);

        Variant<Boolean> primaryProperty = new Variant<>(true);
        serviceMap.put(SERVICE_PRIMARY_PROPERTY_KEY, primaryProperty);

        Variant<Path[]> characteristicsPat = new Variant<>(getCharacteristicsPathArray());
        serviceMap.put(SERVICE_CHARACTERISTIC_PROPERTY_KEY, characteristicsPat);

        Map<String, Map<String, Variant<?>>> externalMap = new HashMap<>();
        externalMap.put(GATT_SERVICE_INTERFACE, serviceMap);

        return externalMap;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public <A> A Get(String interface_name, String property_name) {
        return null;
    }

    @Override
    public <A> void Set(String interface_name, String property_name, A value) {
    }

    @Override
    public Map<String, Variant<?>> GetAll(String interfaceName) {
        if (GATT_SERVICE_INTERFACE.equals(interfaceName)) {
            return this.getProperties().get(GATT_SERVICE_INTERFACE);
        }
        throw new RuntimeException("Wrong interface [interface_name=" + interfaceName + "]");
    }
}

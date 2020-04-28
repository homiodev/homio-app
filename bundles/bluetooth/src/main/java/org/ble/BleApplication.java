package org.ble;

import org.bluez.GattApplication1;
import org.bluez.GattManager1;
import org.bluez.GattService1;
import org.bluez.LEAdvertisingManager1;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.dbus.ObjectManager;
import org.freedesktop.DBus;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BleApplication class is the starting point of the entire Peripheral service's structure.
 * It is responsible of the service's publishment and and the advertisement.
 */
public class BleApplication implements GattApplication1 {

    public static final String BLUEZ_DEVICE_INTERFACE = "org.bluez.Device1";
    public static final String BLUEZ_GATT_INTERFACE = "org.bluez.GattManager1";
    public static final String BLUEZ_LE_ADV_INTERFACE = "org.bluez.LEAdvertisingManager1";
    private static final String DBUS_BUSNAME = "org.freedesktop.DBus";
    private static final String BLUEZ_DBUS_BUSNAME = "org.bluez";
    private static final String BLUEZ_ADAPTER_INTERFACE = "org.bluez.Adapter1";
    BleService bleService;
    String path;
    private String adapterPath;
    private BleAdvertisement adv;
    private String adapterAlias;

    private boolean hasDeviceConnected = false;

    private DBusSigHandler<InterfacesAdded> interfacesAddedSignalHandler;
    private DBusSigHandler<InterfacesRemoved> interfacesRemovedSignalHandler;
    private BleApplicationListener listener;
    private DBusConnection dbusConnection;

    public BleApplication(String path, String serviceUUID, BleApplicationListener listener) {
        this.path = path;
        this.listener = listener;
        this.bleService = new BleService(path + "/s", serviceUUID);

        String advPath = path + "/advertisement";
        this.adv = new BleAdvertisement(BleAdvertisement.AdvertisementType.peripheral, advPath);
    }

    public void start() throws DBusException {
        this.dbusConnection = DBusConnection.getConnection(DBusConnection.SYSTEM);

        adapterPath = findAdapterPath();
        if (adapterPath == null) {
            throw new RuntimeException("No BLE adapter found");
        }

        Properties adapterProperties = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, Properties.class);
        adapterProperties.Set(BLUEZ_ADAPTER_INTERFACE, "Powered", new Variant<>(true));
        if (adapterAlias != null) {
            adapterProperties.Set(BLUEZ_ADAPTER_INTERFACE, "Alias", new Variant<>(adapterAlias));
        }

        GattManager1 gattManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, GattManager1.class);

        LEAdvertisingManager1 advManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, LEAdvertisingManager1.class);

        if (!adv.hasServices()) {
            adv.addService(bleService);
        }
        export();

        Map<String, Variant> advOptions = new HashMap<>();
        advManager.RegisterAdvertisement(adv, advOptions);

        Map<String, Variant> appOptions = new HashMap<>();
        gattManager.RegisterApplication(this, appOptions);

        initInterfacesHandler();
    }

    public void stop() throws DBusException {
        if (adapterPath == null) {
            return;
        }
        GattManager1 gattManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, GattManager1.class);
        LEAdvertisingManager1 advManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, LEAdvertisingManager1.class);

        if (adv != null) {
            advManager.UnregisterAdvertisement(adv);
        }
        gattManager.UnregisterApplication(this);

        unExport();
        dbusConnection.removeSigHandler(InterfacesAdded.class, interfacesAddedSignalHandler);
        dbusConnection.removeSigHandler(InterfacesRemoved.class, interfacesRemovedSignalHandler);
        dbusConnection.disconnect();
        dbusConnection = null;
    }

    protected void initInterfacesHandler() throws DBusException {
        DBus dbus = dbusConnection.getRemoteObject(DBUS_BUSNAME, "/or/freedesktop/DBus", DBus.class);
        String bluezDbusBusName = dbus.GetNameOwner(BLUEZ_DBUS_BUSNAME);
        ObjectManager bluezObjectManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);

        interfacesAddedSignalHandler = signal -> {
            Map<String, Variant> iamap = signal.getInterfacesAdded().get(GattService1.class.getName());
            if (iamap != null) {
                Variant<String> address = iamap.get("Address");
                if (address != null) {
                    hasDeviceConnected = true;
                    listener.deviceConnected(address, signal);
                }
            }
        };

        interfacesRemovedSignalHandler = signal -> {
            List<String> irlist = signal.getInterfacesRemoved();
            for (String ir : irlist) {
                if (GattService1.class.getName().equals(ir)) {
                    hasDeviceConnected = false;
                    listener.deviceDisconnected(signal);
                }
            }
        };

        dbusConnection.addSigHandler(InterfacesAdded.class, bluezDbusBusName, bluezObjectManager, interfacesAddedSignalHandler);
        dbusConnection.addSigHandler(InterfacesRemoved.class, bluezDbusBusName, bluezObjectManager, interfacesRemovedSignalHandler);
    }

    /**
     * Set the alias name of the peripheral. This name is visible by the central that discover s peripheral.
     * This must set before start to take effect.
     */
    public void setAdapterAlias(String alias) {
        adapterAlias = alias;
    }

    public boolean hasDeviceConnected() {
        return hasDeviceConnected;
    }

    public BleAdvertisement getAdvertisement() {
        return adv;
    }

    /**
     * Search for a Adapter that has GattManager1 and LEAdvertisement1 interfaces, otherwise return null.
     */
    private String findAdapterPath() throws DBusException {
        ObjectManager bluezObjectManager = dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
        if (bluezObjectManager == null) {
            return null;
        }

        Map<Path, Map<String, Map<String, Variant>>> bluezManagedObject = bluezObjectManager.GetManagedObjects();
        if (bluezManagedObject == null) {
            return null;
        }

        for (Path path : bluezManagedObject.keySet()) {
            Map<String, Map<String, Variant>> value = bluezManagedObject.get(path);
            boolean hasGattManager = false;
            boolean hasAdvManager = false;

            for (String key : value.keySet()) {
                if (key.equals(BLUEZ_GATT_INTERFACE)) {
                    hasGattManager = true;
                }
                if (key.equals(BLUEZ_LE_ADV_INTERFACE)) {
                    hasAdvManager = true;
                }

                if (hasGattManager && hasAdvManager) {
                    return path.toString();
                }
            }
        }

        return null;
    }

    /**
     * Export the application in Dbus system.
     */
    private void export() throws DBusException {
        if (adv != null) {
            adv.export(dbusConnection);
        }
        bleService.export(dbusConnection);
        dbusConnection.exportObject(path, this);
    }

    /**
     * Unexport the application in Dbus system.
     */
    private void unExport() throws DBusException {
        if (adv != null) {
            adv.unexport(dbusConnection);
        }
        bleService.unexport(dbusConnection);
        dbusConnection.unExportObject(path);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    public Map<Path, Map<String, Map<String, Variant<?>>>> GetManagedObjects() {
        Map<Path, Map<String, Map<String, Variant<?>>>> response = new HashMap<>();
        response.put(bleService.getPath(), bleService.getProperties());
        for (BleCharacteristic characteristic : bleService.getCharacteristics()) {
            response.put(characteristic.getPath(), characteristic.getProperties());
        }
        return response;
    }
}

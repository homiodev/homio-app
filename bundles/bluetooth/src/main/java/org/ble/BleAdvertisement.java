package org.ble;

import org.bluez.LEAdvertisement1;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BleAdvertisement implements LEAdvertisement1, Properties {

    private static final String LEADVERTISEMENT_INTERFACE = "org.bluez.LEAdvertisement1";
    private static final String ADVERTISEMENT_TYPE_PROPERTY_KEY = "Type";
    private static final String ADVERTISEMENT_SERVICES_UUIDS_PROPERTY_KEY = "ServiceUUIDs";
    private static final String ADVERTISEMENT_SOLICIT_UUIDS_PROPERTY_KEY = "SolicitUUIDs";
    private static final String ADVERTISEMENT_MANUFACTURER_DATA_PROPERTY_KEY = "ManufacturerData";
    private static final String ADVERTISEMENT_SERVICE_DATA_PROPERTY_KEY = "ServiceData";
    private static final String ADVERTISEMENT_INCLUDE_TX_POWER_PROPERTY_KEY = "IncludeTxPower";
    private BleAdvertisement.AdvertisementType type;
    private List<String> servicesUUIDs;
    private Map<Integer, Integer> manufacturerData;
    private List<String> solicitUUIDs;
    private Map<String, Integer> serviceData;
    private boolean includeTxPower = true;
    private String path;

    public BleAdvertisement(BleAdvertisement.AdvertisementType type, String path) {
        this.type = type;
        this.path = path;
        this.servicesUUIDs = new ArrayList<>();
        this.solicitUUIDs = new ArrayList<>();
    }

    public void addService(BleService service) {
        this.servicesUUIDs.add(service.getUuid());
    }

    public void addSolicited(BleService service) {
        this.solicitUUIDs.add(service.getUuid());
    }

    public void setManufacturerData(Map<Integer, Integer> manufacturerData) {
        this.manufacturerData = manufacturerData;
    }

    public void setServiceData(Map<String, Integer> serviceData) {
        this.serviceData = serviceData;
    }

    public void setIncludeTxPower(boolean includeTxPower) {
        this.includeTxPower = includeTxPower;
    }

    public boolean hasServices() {
        return servicesUUIDs != null && !servicesUUIDs.isEmpty();
    }

    void export(DBusConnection dbusConnection) throws DBusException {
        dbusConnection.exportObject(this.getPath().toString(), this);
    }

    protected void unexport(DBusConnection dBusConnection) {
        dBusConnection.unExportObject(this.getPath().toString());
    }

    public Path getPath() {
        return new Path(path);
    }

    public Map<String, Map<String, Variant<?>>> getProperties() {
        System.out.println("Advertisement -> getAdvertisementProperties");

        Map<String, Variant<?>> advertisementMap = new HashMap<>();

        Variant<String> Type = new Variant<>(this.type.name());
        advertisementMap.put(ADVERTISEMENT_TYPE_PROPERTY_KEY, Type);

        if (servicesUUIDs != null && !servicesUUIDs.isEmpty()) {
            Variant<String[]> serviceUUIDs = new Variant<>(this.servicesUUIDs.toArray(new String[0]));
            advertisementMap.put(ADVERTISEMENT_SERVICES_UUIDS_PROPERTY_KEY, serviceUUIDs);
        }
        if (solicitUUIDs != null && !solicitUUIDs.isEmpty()) {
            Variant<String[]> solicitUUIDs = new Variant<>(this.solicitUUIDs.toArray(new String[0]));
            advertisementMap.put(ADVERTISEMENT_SOLICIT_UUIDS_PROPERTY_KEY, solicitUUIDs);
        }
        if (manufacturerData != null) {
            Variant<Map<Integer, Integer>> manufacturerData = new Variant<>(this.manufacturerData);
            advertisementMap.put(ADVERTISEMENT_MANUFACTURER_DATA_PROPERTY_KEY, manufacturerData);
        }
        if (serviceData != null) {
            Variant<Map<String, Integer>> serviceData = new Variant<>(this.serviceData);
            advertisementMap.put(ADVERTISEMENT_SERVICE_DATA_PROPERTY_KEY, serviceData);
        }

        Variant<Boolean> includeTxPower = new Variant<>(this.includeTxPower);
        advertisementMap.put(ADVERTISEMENT_INCLUDE_TX_POWER_PROPERTY_KEY, includeTxPower);

        Map<String, Map<String, Variant<?>>> externalMap = new HashMap<>();
        externalMap.put(LEADVERTISEMENT_INTERFACE, advertisementMap);

        return externalMap;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void Release() {
        // TODO Auto-generated method stub
        System.out.println("LE Advertisement Release called !!");
    }

    @Override
    public <A> A Get(String arg0, String arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <A> void Set(String arg0, String arg1, A arg2) {
        // TODO Auto-generated method stub
    }

    @Override
    public Map<String, Variant<?>> GetAll(String interfaceName) {
        if (LEADVERTISEMENT_INTERFACE.equals(interfaceName)) {
            return this.getProperties().get(LEADVERTISEMENT_INTERFACE);
        }
        throw new RuntimeException("Wrong interface [interface_name=" + interfaceName + "]");
    }

    public enum AdvertisementType {
        broadcast, peripheral
    }

}

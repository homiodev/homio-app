package org.ble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.bluez.GattCharacteristic1;
import org.dbus.PropertiesChangedSignal;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;


@RequiredArgsConstructor
class BleCharacteristic implements GattCharacteristic1, Properties {

    private static final String GATT_CHARACTERISTIC_INTERFACE = "org.bluez.GattCharacteristic1";
    private static final String CHARACTERISTIC_SERVICE_PROPERTY_KEY = "Service";
    private static final String CHARACTERISTIC_UUID_PROPERTY_KEY = "UUID";
    private static final String CHARACTERISTIC_FLAGS_PROPERTY_KEY = "Flags";
    private static final String CHARACTERISTIC_DESCRIPTORS_PROPERTY_KEY = "Descriptors";
    private static final String CHARACTERISTIC_VALUE_PROPERTY_KEY = "Value";
    final String uuid;
    private final String path; //The absolute path, APPLICATION/SERVICE/CHARACTERISTIC
    private final BleService service;
    private final List<CharacteristicFlag> flags;
    @Setter
    private Consumer<byte[]> writeListener;

    @Setter
    private Supplier<byte[]> readListener;

    @Setter
    private int minReadTimeout = 5000;

    private long lastReadTime = -1;
    private long lastWriteTime = -1;

    @Setter
    private byte[] value = new byte[0];

    private boolean isNotifying = false;
    private boolean writeStarted;
    private long lastIntermediateWriteTime = -1;
    private Integer writeByteLength;
    private Integer writeByteIndex;
    private byte[] packet;

    void export(DBusConnection dbusConnection) throws DBusException {
        dbusConnection.exportObject(this.getPath().toString(), this);
    }

    void unexport(DBusConnection dBusConnection) {
        dBusConnection.unExportObject(this.getPath().toString());
    }

    /**
     * Return the Path (dbus class)
     */
    public Path getPath() {
        return new Path(path);
    }

    public Map<String, Map<String, Variant<?>>> getProperties() {
        Map<String, Variant<?>> characteristicMap = new HashMap<>();

        Variant<Path> servicePathProperty = new Variant<>(service.getPath());
        characteristicMap.put(CHARACTERISTIC_SERVICE_PROPERTY_KEY, servicePathProperty);

        Variant<String> uuidProperty = new Variant<>(this.uuid);
        characteristicMap.put(CHARACTERISTIC_UUID_PROPERTY_KEY, uuidProperty);

        Variant<String[]> flagsProperty = new Variant<>(this.flags.stream().map(CharacteristicFlag::toString).toArray(String[]::new));
        characteristicMap.put(CHARACTERISTIC_FLAGS_PROPERTY_KEY, flagsProperty);

        Variant<Path[]> descriptorsPatProperty = new Variant<>(new Path[0]);
        characteristicMap.put(CHARACTERISTIC_DESCRIPTORS_PROPERTY_KEY, descriptorsPatProperty);

        Map<String, Map<String, Variant<?>>> externalMap = new HashMap<>();
        externalMap.put(GATT_CHARACTERISTIC_INTERFACE, characteristicMap);

        return externalMap;
    }

    /**
     * Call this method to send a notification to a central.
     */
    public void sendNotification() {
        try {
            DBusConnection dbusConnection = DBusConnection.getConnection(DBusConnection.SYSTEM);

            Variant<byte[]> signalValueVariant = new Variant<>(value);
            Map<String, Variant> signalValue = new HashMap<>();
            signalValue.put(BleCharacteristic.CHARACTERISTIC_VALUE_PROPERTY_KEY, signalValueVariant);

            PropertiesChangedSignal.PropertiesChanged signal = new PropertiesChangedSignal.PropertiesChanged(this.getPath().toString(), GATT_CHARACTERISTIC_INTERFACE, signalValue, new ArrayList<>());
            dbusConnection.sendSignal(signal);
            dbusConnection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    /**
     * This method is called when the central request the Characteristic's value.
     */
    @Override
    public byte[] ReadValue(Map<String, Variant> option) {
        int offset = 0;
        if (option.get("offset") != null) {
            Variant<UInt16> vOffset = option.get("offset");
            offset = (vOffset.getValue() != null) ? vOffset.getValue().intValue() : offset;
        }

        if (readListener != null && System.currentTimeMillis() - lastReadTime >= minReadTimeout) {
            lastReadTime = System.currentTimeMillis();
            try {
                value = readListener.get();
            } catch (Exception ex) {
                System.err.printf("Error while read from ble: '%s'%n", ex.getMessage());
                value = new byte[0];
            }
        }
        System.out.printf("Request value from characteristic: '%s', value: '%s'%n", path, new String(value));
        return Arrays.copyOfRange(value, offset, value.length);
    }

    /**
     * This method is called when the central want to write the Characteristic's value.
     */
    @Override
    public void WriteValue(byte[] value, Map<String, Variant> option) {
        if (writeListener != null) {

            if (lastIntermediateWriteTime != -1 && System.currentTimeMillis() - lastIntermediateWriteTime > 5000) {
                System.err.println("Too big timeout between writing packages");
                this.resetWrite();
            }

            System.out.printf("Response characteristic '%s' value '%s'%n", uuid, new String(value));

            if (!this.writeStarted && value.length > 3 && value[0] == '@' && value[1] == '#' && value[2] == '@') {
                try {
                    this.writeByteLength = Integer.valueOf(new String(value, 3, value.length - 3));
                    if (this.writeByteLength > 65536) {
                        throw new RuntimeException("Unable to handle packet more that 65536 bytes");
                    }
                    this.packet = new byte[this.writeByteLength];
                    this.writeStarted = true;
                    this.writeByteIndex = 0;
                    this.lastIntermediateWriteTime = System.currentTimeMillis();
                    return;
                } catch (Exception ex) {
                    System.err.println("Error start writing. Unable to parse data length");
                    return;
                }
            }

            for (byte val : value) {
                this.packet[writeByteIndex++] = val;
            }

            this.lastIntermediateWriteTime = System.currentTimeMillis();
            if (this.writeByteIndex < this.writeByteLength) {
                return;
            }

            lastWriteTime = System.currentTimeMillis();
            try {
                writeListener.accept(this.packet);
                this.resetWrite();
            } catch (Exception ex) {
                System.err.printf("Error write from ble: %s%n", ex.getMessage());
            }
        }
    }

    private void resetWrite() {
        this.lastIntermediateWriteTime = -1;
        this.writeStarted = false;
        this.packet = null;
    }

    @Override
    public void StartNotify() {
        if (isNotifying) {
            System.err.printf("Characteristic already notifying");
            return;
        }
        this.isNotifying = true;
    }

    @Override
    public void StopNotify() {
        if (!isNotifying) {
            System.err.printf("Characteristic already not notifying");
            return;
        }
        this.isNotifying = false;
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
        if (GATT_CHARACTERISTIC_INTERFACE.equals(interfaceName)) {
            return this.getProperties().get(GATT_CHARACTERISTIC_INTERFACE);
        }
        throw new RuntimeException("Wrong interface [interface_name=" + interfaceName + "]");
    }
}

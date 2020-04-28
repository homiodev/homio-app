package org.ble;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.ble.CharacteristicFlag.*;

@Log4j2
public class BluetoothApplication {

    @Getter
    private final BleApplication bleApplication;

    public BluetoothApplication(String name, String serviceUUID, BleApplicationListener bleApplicationListener) {
        bleApplication = new BleApplication("/" + name, serviceUUID, bleApplicationListener);
    }

    public ValueConsumer newReadWriteCharacteristic(String name, String uuid, Consumer<byte[]> writeValueListener, Supplier<byte[]> readValueListener) {
        BleCharacteristic characteristic = new BleCharacteristic(uuid, bleApplication.path + "/" + name, bleApplication.bleService, C_READ_WRITE);
        characteristic.setWriteListener(writeValueListener);
        characteristic.setReadListener(readValueListener);
        bleApplication.bleService.getCharacteristics().add(characteristic);
        return value -> characteristic.setValue(value.getBytes());
    }

    public ValueConsumer newReadCharacteristic(String name, String uuid, Supplier<byte[]> readValueListener) {
        BleCharacteristic characteristic = new BleCharacteristic(uuid, bleApplication.path + "/" + name, bleApplication.bleService, C_READ);
        characteristic.setReadListener(readValueListener);
        bleApplication.bleService.getCharacteristics().add(characteristic);
        return value -> characteristic.setValue(value.getBytes());
    }

    public ValueNotifyConsumer newReadWriteNotifyCharacteristic(String name, String uuid, Consumer<byte[]> readValueListener) {
        final BleCharacteristic characteristic = new BleCharacteristic(uuid, bleApplication.path + "/" + bleApplication, bleApplication.bleService, C_READ_WRITE_NOTIFY);
        characteristic.setWriteListener(readValueListener);
        bleApplication.bleService.getCharacteristics().add(characteristic);
        return createSetValueNotify(characteristic);
    }

    public ValueConsumer newWriteCharacteristic(String name, String uuid) {
        final BleCharacteristic characteristic = new BleCharacteristic(uuid, bleApplication.path + "/" + bleApplication, bleApplication.bleService, C_WRITE);
        bleApplication.bleService.getCharacteristics().add(characteristic);
        return value -> characteristic.setValue(value.getBytes());
    }

    public ValueNotifyConsumer newWriteNotifyCharacteristic(String name, String uuid) {
        final BleCharacteristic characteristic = new BleCharacteristic(uuid, bleApplication.path + "/" + name, bleApplication.bleService, C_READ_NOTIFY);
        bleApplication.bleService.getCharacteristics().add(characteristic);
        return createSetValueNotify(characteristic);
    }

    private ValueNotifyConsumer createSetValueNotify(BleCharacteristic characteristic) {
        return new ValueNotifyConsumer() {
            @Override
            public void setValue(String value) {
                characteristic.setValue(value.getBytes());
            }

            @Override
            public void setValueAndNotify(String value) {
                characteristic.setValue(value.getBytes());
                characteristic.sendNotification();
            }
        };
    }

    public void start() throws DBusException {
        this.bleApplication.start();
    }

    public String gatherWriteBan() {
        List<String> status = new ArrayList<>();
        for (BleCharacteristic characteristic : bleApplication.bleService.getCharacteristics()) {
            if (characteristic.isBanOnWrite()) {
                status.add(characteristic.uuid + "%&%" + characteristic.secToReleaseBan());
            }
        }
        return String.join("%#%", status);
    }

    public interface ValueConsumer {
        void setValue(String value);
    }

    public interface ValueNotifyConsumer {
        void setValue(String value);

        void setValueAndNotify(String value);
    }
}

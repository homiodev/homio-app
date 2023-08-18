package org.homio.app.ble;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.ble.BleApplicationListener;
import org.ble.BluetoothApplication;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;
import org.homio.hquery.hardware.network.Network;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;

@RequiredArgsConstructor
public abstract class BaseBluetoothCharacteristicService {

    private static final String PREFIX = "13333333-3333-3333-3333-3333333330";
    private static final String SERVICE_UUID = PREFIX + "00";
    private static final String WIFI_UUID = PREFIX + "10";
    private static final String DATA_UUID = PREFIX + "20";
    private static final String selectedWifiInterface = "wlan0";
    @Getter
    private final MachineHardwareRepository machineHardwareRepository;
    @Getter
    private final NetworkHardwareRepository networkHardwareRepository;

    public String getDeviceCharacteristic(String uuid) {
        switch (uuid) {
            case DATA_UUID -> {
                return getData();
            }
            case WIFI_UUID -> {
                return readWifiList();
            }
        }
        return null;
    }

    public void setDeviceCharacteristic(String uuid, byte[] value) {
        switch (uuid) {
            case DATA_UUID -> rebootDevice(value);
            case WIFI_UUID -> writeWifiSSID(value);
        }
    }

    public void init() {
        System.out.println("Starting bluetooth...");

        if (!SystemUtils.IS_OS_LINUX) {
            System.out.println("Bluetooth skipped for non linux env. Require unix sockets");
            updateBluetoothStatus("OFFLINE", "Non linux env");
            return;
        }

        BluetoothApplication bluetoothApplication = new BluetoothApplication("homio", SERVICE_UUID, new BleApplicationListener() {
            @Override
            public void deviceConnected(Variant<String> address, InterfacesAdded signal) {
                System.out.printf("Device connected. Address: '%s'. Path: '%s'%n", address.getValue(), signal.getObjectPath());
            }

            @Override
            public void deviceDisconnected(InterfacesRemoved signal) {
                System.out.printf("Device disconnected. Path: '%s'%n", signal.getObjectPath());
            }
        });

        bluetoothApplication.newReadWriteCharacteristic("wifi_list", WIFI_UUID, this::writeWifiSSID, () -> readWifiList().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("data", DATA_UUID, this::rebootDevice, () -> getData().getBytes());

        // start ble
        try {
            bluetoothApplication.start();
            System.out.println("Bluetooth successfully started");
            updateBluetoothStatus("ONLINE", null);
        } catch (Throwable ex) {
            updateBluetoothStatus("ERROR#~#" + ex.getMessage(), ex.getMessage());
            System.err.printf("Unable to start bluetooth service: '%s'%n", ex.getMessage());
        }
    }

    @SneakyThrows
    private String getData() {
        try {
            return new ObjectMapper().writeValueAsString(new MachineSummary());
        } catch (Exception ex) {
            System.err.printf("Error during reading: %s%n", ex.getMessage());
            throw ex;
        }
    }

    public abstract void updateBluetoothStatus(String status, String message);

    protected <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private void rebootDevice(byte[] ignore) {
        if (SystemUtils.IS_OS_LINUX) {
            machineHardwareRepository.reboot();
        }
    }

    private void writeWifiSSID(byte[] bytes) {
        String[] split = new String(bytes).split("%&%");
        if (split.length == 3 && split[1].length() >= 6) {
            if (SystemUtils.IS_OS_LINUX) {
                System.out.println("Writing wifi credentials");
                networkHardwareRepository.setWifiCredentials(split[0], split[1], split[2]);
                networkHardwareRepository.restartNetworkInterface(selectedWifiInterface);
            }
            // this script should connect to router or run hotspot
            // TODO: do we need this???             machineHardwareRepository.execute("/usr/bin/autohotspot", 60);
        }
    }

    private String readWifiList() {
        if (SystemUtils.IS_OS_LINUX) {
            return networkHardwareRepository
                    .scan(selectedWifiInterface).stream()
                    .filter(distinctByKey(Network::getSsid))
                    .map(n -> n.getSsid() + "%&%" + n.getStrength()).collect(Collectors.joining("%#%"));
        }
        ArrayList<String> result = machineHardwareRepository
                .executeNoErrorThrowList("netsh wlan show profiles", 60, null);
        return result.stream()
                .filter(s -> s.contains("All User Profile"))
                .map(s -> s.substring(s.indexOf(":") + 1).trim())
                .map(s -> s + "%&%-").collect(Collectors.joining("%#%"));
    }

    @Getter
    public class MachineSummary {

        private final String mac = networkHardwareRepository.getMacAddress();
        private final String model = SystemUtils.OS_NAME;
        private final String wifi = networkHardwareRepository.getWifiName();
        private final String ip = networkHardwareRepository.getIPAddress();
        private final String time = machineHardwareRepository.getUptime();
        private final String memory = machineHardwareRepository.getRamMemory();
        private final String disc = machineHardwareRepository.getDiscCapacity();
        private final boolean net = networkHardwareRepository.pingAddress("www.google.com", 80, 5000);
        private final boolean linux = SystemUtils.IS_OS_LINUX;
    }
}

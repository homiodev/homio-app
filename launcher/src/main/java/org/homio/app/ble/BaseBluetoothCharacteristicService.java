package org.homio.app.ble;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.ble.BleApplicationListener;
import org.ble.BluetoothApplication;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;
import org.homio.hquery.hardware.network.Network;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;

@Log4j2
@RequiredArgsConstructor
public abstract class BaseBluetoothCharacteristicService {

    public static final int MIN_WRITE_TIMEOUT = 10000; // 10 sec
    private static final String PREFIX = "13333333-3333-3333-3333-3333333330";
    private static final String SERVICE_UUID = PREFIX + "00";
    private static final String CPU_LOAD_UUID = PREFIX + "01";
    private static final String CPU_TEMP_UUID = PREFIX + "02";
    private static final String DEVICE_MODEL_UUID = PREFIX + "03";
    private static final String MEMORY_UUID = PREFIX + "04";
    private static final String UPTIME_UUID = PREFIX + "05";
    private static final String WIFI_NAME_UUID = PREFIX + "06";
    private static final String IP_ADDRESS_UUID = PREFIX + "07";
    private static final String LAST_ERROR_UUID = PREFIX + "08";
    private static final String MAC_UUID = PREFIX + "09";
    private static final String WIFI_LIST_UUID = PREFIX + "10";
    private static final String SD_MEMORY_UUID = PREFIX + "11";
    private static final String WRITE_BAN_UUID = PREFIX + "12";
    private static final String HAS_WWW_ACCESS_UUID = PREFIX + "15";
    private static String selectedWifiInterface = "wlan0";
    @Getter
    private final MachineHardwareRepository machineHardwareRepository;
    private final Map<String, Long> wifiWriteProtect = new ConcurrentHashMap<>();
    private BluetoothApplication bluetoothApplication;
    @Getter
    private final NetworkHardwareRepository networkHardwareRepository;
    private String lastError = "none";

    public String getDeviceCharacteristic(String uuid) {
        switch (uuid) {
            case LAST_ERROR_UUID:
                return lastError;
            case CPU_LOAD_UUID:
                return readIfLinux(machineHardwareRepository::getCpuLoad);
            case CPU_TEMP_UUID:
                return readIfLinux(this::getCpuTemp);
            case MEMORY_UUID:
                return readIfLinux(machineHardwareRepository::getMemory);
            case SD_MEMORY_UUID:
                return readIfLinux(() -> machineHardwareRepository.getSDCardMemory().toString());
            case UPTIME_UUID:
                return readIfLinux(machineHardwareRepository::getUptime);
            case IP_ADDRESS_UUID:
                return readSafeValue(networkHardwareRepository::getIPAddress);
            case HAS_WWW_ACCESS_UUID:
                return readSafeValue(this::hasInternetAccess);
            case MAC_UUID:
                return readSafeValue(networkHardwareRepository::getMacAddress);
            case WRITE_BAN_UUID:
                return gatherWriteBan();
            case DEVICE_MODEL_UUID:
                return machineHardwareRepository.getDeviceModel();
            case WIFI_LIST_UUID:
                return readIfLinux(this::readWifiList);
            case WIFI_NAME_UUID:
                return readIfLinux(this::getWifiName);
        }
        return null;
    }

    public void setDeviceCharacteristic(String uuid, byte[] value) {
        if (value != null && (!wifiWriteProtect.containsKey(uuid) ||
            System.currentTimeMillis() - wifiWriteProtect.get(uuid) > MIN_WRITE_TIMEOUT)) {
            wifiWriteProtect.put(uuid, System.currentTimeMillis());
            switch (uuid) {
                case DEVICE_MODEL_UUID -> {
                    rebootDevice(null);
                }
                case WIFI_NAME_UUID -> writeWifiSSID(value);
            }
        }
    }

    public void init() {
        log.info("Starting bluetooth...");

        if (!isLinuxEnvironment()) {
            log.warn("Bluetooth skipped for non linux env. Require unix sockets");
            updateBluetoothStatus("OFFLINE", "Non linux env");
            return;
        }

        bluetoothApplication = new BluetoothApplication("homio", SERVICE_UUID, new BleApplicationListener() {
            @Override
            public void deviceConnected(Variant<String> address, InterfacesAdded signal) {
                log.info("Device connected. Address: <{}>. Path: <{}>", address.getValue(), signal.getObjectPath());
            }

            @Override
            public void deviceDisconnected(InterfacesRemoved signal) {
                log.info("Device disconnected. Path: <{}>", signal.getObjectPath());
            }
        });

        bluetoothApplication.newReadCharacteristic("cpu_load", CPU_LOAD_UUID,
            () -> readIfLinux(machineHardwareRepository::getCpuLoad).getBytes());
        bluetoothApplication.newReadCharacteristic("cpu_temp", CPU_TEMP_UUID, () -> readIfLinux(this::getCpuTemp).getBytes());
        bluetoothApplication.newReadCharacteristic("memory", MEMORY_UUID,
            () -> readIfLinux(machineHardwareRepository::getMemory).getBytes());
        bluetoothApplication.newReadCharacteristic("sd_memory", SD_MEMORY_UUID,
            () -> readIfLinux(() -> machineHardwareRepository.getSDCardMemory().toString()).getBytes());
        bluetoothApplication.newReadCharacteristic("uptime", UPTIME_UUID,
            () -> readIfLinux(machineHardwareRepository::getUptime).getBytes());
        bluetoothApplication.newReadCharacteristic("ip", IP_ADDRESS_UUID,
            () -> readSafeValue(networkHardwareRepository::getIPAddress).getBytes());
        bluetoothApplication.newReadCharacteristic("www", HAS_WWW_ACCESS_UUID,
            () -> readSafeValue(this::hasInternetAccess).getBytes());
        bluetoothApplication.newReadCharacteristic("mac", MAC_UUID,
            () -> readSafeValue(networkHardwareRepository::getMacAddress).getBytes());
        bluetoothApplication.newReadCharacteristic("write_ban", WRITE_BAN_UUID,
            () -> bluetoothApplication.gatherWriteBan().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("device_model", DEVICE_MODEL_UUID, this::rebootDevice,
            () -> readIfLinux(machineHardwareRepository::getDeviceModel).getBytes());
        bluetoothApplication.newReadCharacteristic("wifi_list", WIFI_LIST_UUID, () -> readIfLinux(this::readWifiList).getBytes());
        bluetoothApplication.newReadWriteCharacteristic("wifi_name", WIFI_NAME_UUID, this::writeWifiSSID,
            () -> readIfLinux(this::getWifiName).getBytes());
        bluetoothApplication.newReadCharacteristic("last_error", LAST_ERROR_UUID, () -> lastError.getBytes());

        // start ble
        try {
            bluetoothApplication.start();
            log.info("Bluetooth successfully started");
            updateBluetoothStatus("ONLINE", null);
        } catch (Throwable ex) {
            updateBluetoothStatus("ERROR#~#" + ex.getMessage(), ex.getMessage());
            log.error("Unable to start bluetooth service", ex);
        }
    }

    private static boolean isLinuxEnvironment() {
        return SystemUtils.IS_OS_LINUX && !"true".equals(System.getProperty("development"));
    }

    public abstract void updateBluetoothStatus(String status, String message);

    protected <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    @SneakyThrows
    protected void writeSafeValue(Runnable runnable) {
        if (hasAccess()) {
            try {
                runnable.run();
            } catch (Exception ex) {
                lastError = "Error write: " + ex.getMessage();
                log.error("Error during write", ex);
            }
        }
    }

    private void rebootDevice(byte[] ignore) {
        writeSafeValue(machineHardwareRepository::reboot);
    }

    private String gatherWriteBan() {
        List<String> status = new ArrayList<>();
        for (Map.Entry<String, Long> entry : wifiWriteProtect.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() < MIN_WRITE_TIMEOUT) {
                status.add(
                    entry.getKey() + "%&%" + ((MIN_WRITE_TIMEOUT - (System.currentTimeMillis() - entry.getValue())) / 1000));
            }
        }
        return String.join("%#%", status);
    }

    private String hasInternetAccess() {
        return Boolean.toString(networkHardwareRepository.pingAddress("www.google.com", 80, 5000));
    }

    private void writeWifiSSID(byte[] bytes) {
        writeSafeValue(() -> {
            String[] split = new String(bytes).split("%&%");
            if (split.length == 3 && split[1].length() >= 8) {
                log.info("Writing wifi credentials");
                networkHardwareRepository.setWifiCredentials(split[0], split[1], split[2]);
                // networkHardwareRepository.restartNetworkInterface(selectedWifiInterface);
                // this script should connect to router or run hotspot
                // TODO: do we need this???             machineHardwareRepository.execute("/usr/bin/autohotspot", 60);
            }
        });
    }

    private String readWifiList() {
        return networkHardwareRepository.scan(selectedWifiInterface).stream()
                                        .filter(distinctByKey(Network::getSsid))
                                        .map(n -> n.getSsid() + "%&%" + n.getStrength()).collect(Collectors.joining("%#%"));
    }

    private String readSafeValue(Supplier<String> supplier) {
        try {
            if (hasAccess()) {
                return supplier.get();
            }
        } catch (Exception ex) {
            lastError = "Error read: " + ex.getMessage();
            log.error("Error during reading", ex);
        }
        return "";
    }

    private String readIfLinux(Supplier<String> supplier) {
        return isLinuxEnvironment() ? readSafeValue(supplier) : "";
    }

    private boolean hasAccess() {
        return true;
    }

    @SneakyThrows
    private String getCpuTemp() {
        return machineHardwareRepository.getCpuTemperature();
    }

    private String getWifiName() {
        return networkHardwareRepository.getWifiName();
    }
}

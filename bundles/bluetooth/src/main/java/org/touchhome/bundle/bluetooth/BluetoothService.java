package org.touchhome.bundle.bluetooth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.ble.BleApplicationListener;
import org.ble.BluetoothApplication;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;
import org.springframework.stereotype.Controller;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.other.RaspberryHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.Network;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.cloud.impl.ServerConnectionStatus;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionMessageSetting;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionStatusSetting;
import org.touchhome.bundle.cloud.setting.CloudServerRestartSetting;

import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.repository.impl.UserRepository.DEFAULT_USER_ID;
import static org.touchhome.bundle.api.util.TouchHomeUtils.distinctByKey;

@Log4j2
@Controller
@RequiredArgsConstructor
public class BluetoothService implements BundleContext {

    private static final String PREFIX = "13333333-3333-3333-3333-3333333330";
    private static final String SERVICE_UUID = PREFIX + "00";
    private static final String CPU_LOAD_UUID = PREFIX + "01";
    private static final String CPU_TEMP_UUID = PREFIX + "02";
    private static final String DEVICE_MODEL_UUID = PREFIX + "03";
    private static final String MEMORY_UUID = PREFIX + "04";
    private static final String UPTIME_UUID = PREFIX + "05";
    private static final String WIFI_NAME_UUID = PREFIX + "06";
    private static final String IP_ADDRESS_UUID = PREFIX + "07";
    private static final String PWD_SET_UUID = PREFIX + "08";
    private static final String KEYSTORE_SET_UUID = PREFIX + "09";
    private static final String WIFI_LIST_UUID = PREFIX + "10";
    private static final String SD_MEMORY_UUID = PREFIX + "11";
    private static final String WRITE_BAN_UUID = PREFIX + "12";
    private static final String SERVER_CONNECTED_UUID = PREFIX + "13";

    private static final int TIME_REFRESH_PASSWORD = 10 * 60 * 1000; // 10min
    private static long timeSinceLastCheckPassword = -1;
    private final EntityContext entityContext;
    private final RaspberryHardwareRepository raspberryHardwareRepository;
    private final WirelessHardwareRepository wirelessHardwareRepository;
    private BluetoothApplication bluetoothApplication;
    private UserEntity user;

    public void init() {
        if (EntityContext.isTestApplication()) {
            log.info("Bluetooth skipped in test applications");
            return;
        }
        this.user = entityContext.getEntity(DEFAULT_USER_ID);

        log.info("Starting bluetooth...");

        bluetoothApplication = new BluetoothApplication("tango", SERVICE_UUID, new BleApplicationListener() {
            @Override
            public void deviceConnected(Variant<String> address, InterfacesAdded signal) {
                log.info("Device connected. Address: <{}>. Path: <{}>", address.getValue(), signal.getObjectPath());
                timeSinceLastCheckPassword = -1;
            }

            @Override
            public void deviceDisconnected(InterfacesRemoved signal) {
                log.info("Device disconnected. Path: <{}>", signal.getObjectPath());
                timeSinceLastCheckPassword = -1;
            }
        });

        bluetoothApplication.newReadCharacteristic("cpu_load", CPU_LOAD_UUID, () -> readSafeValue(raspberryHardwareRepository::getCpuLoad));
        bluetoothApplication.newReadCharacteristic("cpu_temp", CPU_TEMP_UUID, () -> readSafeValue(raspberryHardwareRepository::getCpuTemp));
        bluetoothApplication.newReadCharacteristic("memory", MEMORY_UUID, () -> readSafeValue(raspberryHardwareRepository::getMemory));
        bluetoothApplication.newReadCharacteristic("sd_memory", SD_MEMORY_UUID, () -> readSafeValue(() -> raspberryHardwareRepository.getSDCardMemory().toFineString()));
        bluetoothApplication.newReadCharacteristic("uptime", UPTIME_UUID, () -> readSafeValue(raspberryHardwareRepository::getUptime));
        bluetoothApplication.newReadCharacteristic("ip", IP_ADDRESS_UUID, () -> readSafeValue(raspberryHardwareRepository::getIpAddress));
        bluetoothApplication.newReadCharacteristic("write_ban", WRITE_BAN_UUID, () -> bluetoothApplication.gatherWriteBan().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("device_model", DEVICE_MODEL_UUID, bytes -> writeSafeValue(() -> {
            if (user.getPassword().equals(new String(bytes))) {
                raspberryHardwareRepository.reboot();
            }
        }), () -> readSafeValue(raspberryHardwareRepository::getDeviceModel));

        bluetoothApplication.newReadCharacteristic("server_connected", SERVER_CONNECTED_UUID, () ->
                readSafeValue(() -> {
                    String error = entityContext.getSettingValue(CloudServerConnectionMessageSetting.class);
                    ServerConnectionStatus status = entityContext.getSettingValue(CloudServerConnectionStatusSetting.class);
                    return status.name() + "%&%" + error;
                }));

        bluetoothApplication.newReadCharacteristic("wifi_list", WIFI_LIST_UUID, () -> readSafeValue(() ->
                wirelessHardwareRepository.scan().stream().filter(distinctByKey(Network::getSsid)).map(n -> n.getSsid() + "%&%" + n.getStrength()).collect(Collectors.joining("%#%"))));

        // for set wifi we set wifi/pwd
        bluetoothApplication.newReadWriteCharacteristic("wifi_name", WIFI_NAME_UUID, bytes ->
                writeSafeValue(() -> {
                    String[] split = new String(bytes).split("%&%");
                    if (split.length == 2 && split[1].length() >= 8) {
                        wirelessHardwareRepository.setWifiPassword(split[0], split[1]);
                        wirelessHardwareRepository.restartNetworkInterface();
                    }
                }), () -> readSafeValue(raspberryHardwareRepository::getWifiName));

        // we may set pwd only once for now
        bluetoothApplication.newReadWriteCharacteristic("pwd", PWD_SET_UUID, bytes -> {
            String pwd = new String(bytes);
            if (user.getPassword() == null) {
                entityContext.save(user.setPassword(pwd));
                timeSinceLastCheckPassword = System.currentTimeMillis();
            } else {
                // refresh time
                if (user.getPassword().equals(pwd)) {
                    timeSinceLastCheckPassword = System.currentTimeMillis();
                }
            }
        }, () -> {
            if (user.getPassword() == null) {
                return "none".getBytes();
            } else if (System.currentTimeMillis() - timeSinceLastCheckPassword > TIME_REFRESH_PASSWORD) {
                return "required".getBytes();
            }
            return "ok".getBytes();
        });

        bluetoothApplication.newReadWriteCharacteristic("keystore", KEYSTORE_SET_UUID, bytes ->
                        writeSafeValue(() -> {
                            entityContext.save(user.setKeystore(bytes));
                            entityContext.setSettingValue(CloudServerRestartSetting.class, "");
                        }),
                () -> readSafeValue(() -> String.valueOf(user.getKeystore() != null)));

        // start ble
        try {
            bluetoothApplication.start();
            log.info("Bluetooth successfully started");
        } catch (Exception ex) {
            log.error("Unable to start bluetooth service", ex);
        }
    }

    @Override
    public String getBundleId() {
        return "bluetooth";
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    private void writeSafeValue(Runnable runnable) {
        if (System.currentTimeMillis() - timeSinceLastCheckPassword < TIME_REFRESH_PASSWORD) {
            runnable.run();
        }
    }

    private byte[] readSafeValue(Supplier<String> supplier) {
        if (System.currentTimeMillis() - timeSinceLastCheckPassword < TIME_REFRESH_PASSWORD) {
            return supplier.get().getBytes();
        }
        return new byte[0];
    }
}

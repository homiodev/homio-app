package org.touchhome.bundle.bluetooth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.ble.BleApplicationListener;
import org.ble.BluetoothApplication;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.Network;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.cloud.impl.ServerConnectionStatus;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionMessageSetting;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionStatusSetting;
import org.touchhome.bundle.cloud.setting.CloudServerRestartSetting;
import org.touchhome.bundle.cloud.setting.CloudServerUrlSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String PWD_REQUIRE_UUID = PREFIX + "14";

    private static final int TIME_REFRESH_PASSWORD = 1 * 60 * 1000; // 1min // TODO
    private static long timeSinceLastCheckPassword = -1;

    public static final int MIN_WRITE_TIMEOUT = 60000;

    private BluetoothApplication bluetoothApplication;
    private UserEntity user;

    private final EntityContext entityContext;
    private final PasswordEncoder passwordEncoder;
    private final LinuxHardwareRepository linuxHardwareRepository;
    private final WirelessHardwareRepository wirelessHardwareRepository;
    private final Map<String, Long> wifiWriteProtect = new ConcurrentHashMap<>();

    private boolean passwordBan;
    private Long passwordBanTimeout = 0L;

    public Map<String, String> getDeviceCharacteristics() {
        Map<String, String> map = new HashMap<>();
        map.put(CPU_LOAD_UUID, readSafeValueStr(linuxHardwareRepository::getCpuLoad));
        map.put(CPU_TEMP_UUID, readSafeValueStr(linuxHardwareRepository::getCpuTemp));
        map.put(MEMORY_UUID, readSafeValueStr(linuxHardwareRepository::getMemory));
        map.put(SD_MEMORY_UUID, readSafeValueStr(() -> linuxHardwareRepository.getSDCardMemory().toFineString()));
        map.put(UPTIME_UUID, readSafeValueStr(linuxHardwareRepository::getUptime));
        map.put(IP_ADDRESS_UUID, readSafeValueStr(linuxHardwareRepository::getIpAddress));
        map.put(WRITE_BAN_UUID, gatherWriteBan());
        map.put(DEVICE_MODEL_UUID, readSafeValueStr(linuxHardwareRepository::getDeviceModel));
        map.put(SERVER_CONNECTED_UUID, readSafeValueStr(this::readServerConnected));
        map.put(WIFI_LIST_UUID, readSafeValueStr(this::readWifiList));
        map.put(WIFI_NAME_UUID, readSafeValueStr(linuxHardwareRepository::getWifiName));
        map.put(PWD_SET_UUID, readPwdSet());
        map.put(KEYSTORE_SET_UUID, readSafeValueStr(() -> String.valueOf(user.getKeystore() != null)));
        map.put(PWD_REQUIRE_UUID, readTimeToReleaseSession());

        return map;
    }

    private String gatherWriteBan() {
        List<String> status = new ArrayList<>();
        for (Map.Entry<String, Long> entry : wifiWriteProtect.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() < MIN_WRITE_TIMEOUT) {
                status.add(entry.getKey() + "%&%" + (int) ((MIN_WRITE_TIMEOUT - (System.currentTimeMillis() - entry.getValue())) / 1000));
            }
        }
        return String.join("%#%", status);
    }

    public void setDeviceCharacteristic(String uuid, String value) {
        if (value != null && (!wifiWriteProtect.containsKey(uuid) || System.currentTimeMillis() - wifiWriteProtect.get(uuid) > MIN_WRITE_TIMEOUT)) {
            wifiWriteProtect.put(uuid, System.currentTimeMillis());
            switch (uuid) {
                case DEVICE_MODEL_UUID:
                    rebootDevice(value.getBytes());
                    return;
                case WIFI_NAME_UUID:
                    writeWifiSSID(value.getBytes());
                    return;
                case PWD_SET_UUID:
                    writePwd(value.getBytes());
                    return;
                case KEYSTORE_SET_UUID:
                    writeKeystore(value.getBytes());
                    return;
                case PWD_REQUIRE_UUID:
                    updatePasswordCheck(value.getBytes());
            }
        }
    }

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

        bluetoothApplication.newReadCharacteristic("cpu_load", CPU_LOAD_UUID, () -> readSafeValue(linuxHardwareRepository::getCpuLoad));
        bluetoothApplication.newReadCharacteristic("cpu_temp", CPU_TEMP_UUID, () -> readSafeValue(linuxHardwareRepository::getCpuTemp));
        bluetoothApplication.newReadCharacteristic("memory", MEMORY_UUID, () -> readSafeValue(linuxHardwareRepository::getMemory));
        bluetoothApplication.newReadCharacteristic("sd_memory", SD_MEMORY_UUID, () -> readSafeValue(() -> linuxHardwareRepository.getSDCardMemory().toFineString()));
        bluetoothApplication.newReadCharacteristic("uptime", UPTIME_UUID, () -> readSafeValue(linuxHardwareRepository::getUptime));
        bluetoothApplication.newReadCharacteristic("ip", IP_ADDRESS_UUID, () -> readSafeValue(linuxHardwareRepository::getIpAddress));
        bluetoothApplication.newReadCharacteristic("write_ban", WRITE_BAN_UUID, () -> bluetoothApplication.gatherWriteBan().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("device_model", DEVICE_MODEL_UUID, this::rebootDevice, () -> readSafeValue(linuxHardwareRepository::getDeviceModel));
        bluetoothApplication.newReadCharacteristic("server_connected", SERVER_CONNECTED_UUID, () -> readSafeValue(this::readServerConnected));
        bluetoothApplication.newReadCharacteristic("wifi_list", WIFI_LIST_UUID, () -> readSafeValue(this::readWifiList));
        bluetoothApplication.newReadWriteCharacteristic("wifi_name", WIFI_NAME_UUID, this::writeWifiSSID, () -> readSafeValue(linuxHardwareRepository::getWifiName));
        bluetoothApplication.newReadWriteCharacteristic("pwd", PWD_SET_UUID, this::writePwd, () -> readPwdSet().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("pwd_req", PWD_REQUIRE_UUID, this::updatePasswordCheck, () -> readTimeToReleaseSession().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("keystore", KEYSTORE_SET_UUID, this::writeKeystore,
                () -> readSafeValue(() -> String.valueOf(user.getKeystore() != null)));

        // start ble
        try {
            bluetoothApplication.start();
            log.info("Bluetooth successfully started");
        } catch (Exception ex) {
            log.error("Unable to start bluetooth service", ex);
        }
    }

    private String readTimeToReleaseSession() {
        return Long.toString((TIME_REFRESH_PASSWORD - (System.currentTimeMillis() - timeSinceLastCheckPassword)) / 1000);
    }

    private void updatePasswordCheck(byte[] bytes) {
        if (user.getPassword().equals(new String(bytes))) {
            timeSinceLastCheckPassword = System.currentTimeMillis();
            passwordBan = false;
        } else {
            passwordBan = true;
            passwordBanTimeout = System.currentTimeMillis();
        }
    }

    private void writeKeystore(byte[] bytes) {
        writeSafeValue(() -> {
            entityContext.save(user.setKeystore(bytes));
            entityContext.setSettingValue(CloudServerRestartSetting.class, "");
        });
    }

    /**
     * We may set password only once. If user wants update password, he need pass old password hash
     */
    private void writePwd(byte[] bytes) {
        String[] split = new String(bytes).split("%&%");
        String password = split[0];
        if (user.getPassword() == null) {
            entityContext.save(user.setPassword(password));
            timeSinceLastCheckPassword = System.currentTimeMillis();
        } else if (split.length > 1 && split[1].equals(passwordEncoder.encode(user.getPassword()))) {
            entityContext.save(user.setPassword(password));
        }
    }

    private void writeWifiSSID(byte[] bytes) {
        writeSafeValue(() -> {
            String[] split = new String(bytes).split("%&%");
            if (split.length == 2 && split[1].length() >= 8) {
                wirelessHardwareRepository.setWifiPassword(split[0], split[1]);
                wirelessHardwareRepository.restartNetworkInterface();
            }
        });
    }

    private void rebootDevice(byte[] bytes) {
        writeSafeValue(() -> {
            if (user.getPassword().equals(new String(bytes))) {
                linuxHardwareRepository.reboot();
            }
        });
    }

    @Override
    public String getBundleId() {
        return "bluetooth";
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    private String readPwdSet() {
        if (passwordBan && System.currentTimeMillis() - passwordBanTimeout > TIME_REFRESH_PASSWORD) {
            passwordBan = false;
        }
        if (passwordBan) {
            return "ban:" + (TIME_REFRESH_PASSWORD - (System.currentTimeMillis() - passwordBanTimeout)) / 1000;
        } else if (user.getPassword() == null) {
            return "none";
        } else if (System.currentTimeMillis() - timeSinceLastCheckPassword > TIME_REFRESH_PASSWORD) {
            return "required";
        }
        return "ok";
    }

    private String readWifiList() {
        return wirelessHardwareRepository.scan().stream().filter(distinctByKey(Network::getSsid)).map(n -> n.getSsid() + "%&%" + n.getStrength()).collect(Collectors.joining("%#%"));
    }

    private String readServerConnected() {
        String error = entityContext.getSettingValue(CloudServerConnectionMessageSetting.class);
        ServerConnectionStatus status = entityContext.getSettingValue(CloudServerConnectionStatusSetting.class);
        return status.name() + "%&%" + error + "%&%" + entityContext.getSettingValue(CloudServerUrlSetting.class);
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

    private String readSafeValueStr(Supplier<String> supplier) {
        if (System.currentTimeMillis() - timeSinceLastCheckPassword < TIME_REFRESH_PASSWORD) {
            return supplier.get();
        }
        return "";
    }
}

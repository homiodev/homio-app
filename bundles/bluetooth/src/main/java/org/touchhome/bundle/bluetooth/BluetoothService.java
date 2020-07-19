package org.touchhome.bundle.bluetooth;

import com.pi4j.system.SystemInfo;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.ble.BleApplicationListener;
import org.ble.BluetoothApplication;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.dbus.Variant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.function.CheckedRunnable;
import org.touchhome.bundle.api.hardware.other.LinuxHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.Network;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.cloud.netty.setting.CloudServerRestartSetting;
import org.touchhome.bundle.cloud.setting.CloudProviderSetting;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.util.TouchHomeUtils.distinctByKey;

@Log4j2
@Controller
@RequiredArgsConstructor
public class BluetoothService implements BundleEntrypoint {

    public static final int MIN_WRITE_TIMEOUT = 5000;
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
    private static final String FEATURES_UUID = PREFIX + "14";
    private static final int TIME_REFRESH_PASSWORD = 5 * 60000; // 5 minute for session
    private static long timeSinceLastCheckPassword = -1;
    private final EntityContext entityContext;
    private final LinuxHardwareRepository linuxHardwareRepository;
    private final WirelessHardwareRepository wirelessHardwareRepository;
    private final Map<String, Long> wifiWriteProtect = new ConcurrentHashMap<>();
    private BluetoothApplication bluetoothApplication;
    private UserEntity user;

    public Map<String, String> getDeviceCharacteristics() {
        Map<String, String> map = new HashMap<>();
        map.put(CPU_LOAD_UUID, readSafeValueStr(linuxHardwareRepository::getCpuLoad));
        map.put(CPU_TEMP_UUID, readSafeValueStr(this::getCpuTemp));
        map.put(MEMORY_UUID, readSafeValueStr(linuxHardwareRepository::getMemory));
        map.put(SD_MEMORY_UUID, readSafeValueStr(() -> linuxHardwareRepository.getSDCardMemory().toFineString()));
        map.put(UPTIME_UUID, readSafeValueStr(linuxHardwareRepository::getUptime));
        map.put(IP_ADDRESS_UUID, readSafeValueStrIT(linuxHardwareRepository::getIpAddress));
        map.put(WRITE_BAN_UUID, gatherWriteBan());
        map.put(DEVICE_MODEL_UUID, readSafeValueStr(linuxHardwareRepository::getDeviceModel));
        map.put(SERVER_CONNECTED_UUID, readSafeValueStrIT(this::readServerConnected));
        map.put(WIFI_LIST_UUID, readSafeValueStr(this::readWifiList));
        map.put(WIFI_NAME_UUID, readSafeValueStr(this::getWifiName));
        map.put(KEYSTORE_SET_UUID, readSafeValueStrIT(this::getKeystore));
        map.put(PWD_SET_UUID, readPwdSet());
        map.put(FEATURES_UUID, readSafeValueStr(this::getFeatures));

        return map;
    }

    private String gatherWriteBan() {
        List<String> status = new ArrayList<>();
        for (Map.Entry<String, Long> entry : wifiWriteProtect.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() < MIN_WRITE_TIMEOUT) {
                status.add(entry.getKey() + "%&%" + ((MIN_WRITE_TIMEOUT - (System.currentTimeMillis() - entry.getValue())) / 1000));
            }
        }
        return String.join("%#%", status);
    }

    public void setDeviceCharacteristic(String uuid, byte[] value) {
        if (value != null && (!wifiWriteProtect.containsKey(uuid) || System.currentTimeMillis() - wifiWriteProtect.get(uuid) > MIN_WRITE_TIMEOUT)) {
            wifiWriteProtect.put(uuid, System.currentTimeMillis());
            switch (uuid) {
                case DEVICE_MODEL_UUID:
                    rebootDevice(null);
                    return;
                case WIFI_NAME_UUID:
                    writeWifiSSID(value);
                    return;
                case PWD_SET_UUID:
                    writePwd(value);
                    return;
                case KEYSTORE_SET_UUID:
                    writeKeystore(value);
            }
        }
    }

    public void init() {
        this.user = entityContext.getEntity(ADMIN_USER);
        if (!EntityContext.isLinuxOrDockerEnvironment()) {
            log.info("Bluetooth skipped for non linux env. Require unix sockets");
            return;
        }
        log.info("Starting bluetooth...");

        bluetoothApplication = new BluetoothApplication("touchHome", SERVICE_UUID, new BleApplicationListener() {
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
        bluetoothApplication.newReadCharacteristic("cpu_temp", CPU_TEMP_UUID, () -> readSafeValue(this::getCpuTemp));
        bluetoothApplication.newReadCharacteristic("memory", MEMORY_UUID, () -> readSafeValue(linuxHardwareRepository::getMemory));
        bluetoothApplication.newReadCharacteristic("sd_memory", SD_MEMORY_UUID, () -> readSafeValue(() -> linuxHardwareRepository.getSDCardMemory().toFineString()));
        bluetoothApplication.newReadCharacteristic("uptime", UPTIME_UUID, () -> readSafeValue(linuxHardwareRepository::getUptime));
        bluetoothApplication.newReadCharacteristic("ip", IP_ADDRESS_UUID, () -> readSafeValue(linuxHardwareRepository::getIpAddress));
        bluetoothApplication.newReadCharacteristic("write_ban", WRITE_BAN_UUID, () -> bluetoothApplication.gatherWriteBan().getBytes());
        bluetoothApplication.newReadWriteCharacteristic("device_model", DEVICE_MODEL_UUID, this::rebootDevice, () -> readSafeValue(linuxHardwareRepository::getDeviceModel));
        bluetoothApplication.newReadCharacteristic("server_connected", SERVER_CONNECTED_UUID, () -> readSafeValue(this::readServerConnected));
        bluetoothApplication.newReadCharacteristic("wifi_list", WIFI_LIST_UUID, () -> readSafeValue(this::readWifiList));
        bluetoothApplication.newReadWriteCharacteristic("wifi_name", WIFI_NAME_UUID, this::writeWifiSSID, () -> readSafeValue(this::getWifiName));
        bluetoothApplication.newReadWriteCharacteristic("pwd", PWD_SET_UUID, this::writePwd, () -> readPwdSet().getBytes());
        bluetoothApplication.newReadCharacteristic("features", FEATURES_UUID, () -> readSafeValue(this::getFeatures));

        bluetoothApplication.newReadWriteCharacteristic("keystore", KEYSTORE_SET_UUID, this::writeKeystore,
                () -> readSafeValue(this::getKeystore));

        // start ble
        try {
            bluetoothApplication.start();
            log.info("Bluetooth successfully started");
            entityContext.setFeatureState("Bluetooth", true);
        } catch (Exception ex) {
            entityContext.setFeatureState("Bluetooth", false);
            log.error("Unable to start bluetooth service", ex);
        }
    }

    private String readTimeToReleaseSession() {
        return Long.toString((TIME_REFRESH_PASSWORD - (System.currentTimeMillis() - timeSinceLastCheckPassword)) / 1000);
    }

    @SneakyThrows
    private void writeKeystore(byte[] bytes) {
        writeSafeValue(() -> {
            byte type = bytes[0];
            byte[] content = Arrays.copyOfRange(bytes, 1, bytes.length);
            switch (type) {
                case 3:
                    log.warn("Writing keystore");
                    entityContext.save(user.setKeystore(content));
                    entityContext.setSettingValue(CloudServerRestartSetting.class, null);
                    break;
                case 5:
                    log.warn("Writing private key");
                    Path privateKey = TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome");
                    FileUtils.writeByteArrayToFile(privateKey.toFile(), content);
                    linuxHardwareRepository.setPermissions(privateKey, 600);
                    break;
                case 7:
                    log.warn("Writing public key");
                    Path publicKey = TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome.pub");
                    FileUtils.writeByteArrayToFile(publicKey.toFile(), content);
                    linuxHardwareRepository.setPermissions(publicKey, 600);
                    break;
            }
        });
    }

    /**
     * We may set password only once. If user wants update password, he need pass old password hash
     */
    private void writePwd(byte[] bytes) {
        String[] split = new String(bytes).split("%&%");
        String email = split[0];
        String encodedPassword = split[1];
        String encodedPreviousPassword = split.length > 2 ? split[2] : "";
        if (user.isPasswordNotSet()) {
            log.warn("Set primary admin password for user: <{}>", email);
            entityContext.save(user.setUserId(email).setPassword(encodedPassword));
            this.entityContext.setSettingValue(CloudServerRestartSetting.class, null);
        } else if (StringUtils.isNotEmpty(encodedPreviousPassword) &&
                Objects.equals(user.getUserId(), email) &&
                user.matchPassword(encodedPreviousPassword)) {
            log.warn("Reset primary admin password for user: <{}>", email);
            entityContext.save(user.setPassword(encodedPassword));
            this.entityContext.setSettingValue(CloudServerRestartSetting.class, null);
        }

        if (user.matchPassword(encodedPassword)) {
            timeSinceLastCheckPassword = System.currentTimeMillis();
        }
    }

    private void writeWifiSSID(byte[] bytes) {
        writeSafeValue(() -> {
            String[] split = new String(bytes).split("%&%");
            if (split.length == 3 && split[1].length() >= 8) {
                log.info("Writing wifi credentials");
                wirelessHardwareRepository.setWifiCredentials(split[0], split[1], split[2]);
                wirelessHardwareRepository.restartNetworkInterface();
            }
        });
    }

    private void rebootDevice(byte[] ignore) {
        writeSafeValue(linuxHardwareRepository::reboot);
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
        if (user.isPasswordNotSet()) {
            return "none";
        } else if (System.currentTimeMillis() - timeSinceLastCheckPassword > TIME_REFRESH_PASSWORD) {
            return "required";
        }
        return "ok:" + readTimeToReleaseSession();
    }

    private String readWifiList() {
        return wirelessHardwareRepository.scan(wirelessHardwareRepository.getActiveNetworkInterface()).stream()
                .filter(distinctByKey(Network::getSsid))
                .map(n -> n.getSsid() + "%&%" + n.getStrength()).collect(Collectors.joining("%#%"));
    }

    private String readServerConnected() {
        return entityContext.getSettingValue(CloudProviderSetting.class).getStatus();
    }

    @SneakyThrows
    private void writeSafeValue(CheckedRunnable runnable) {
        if (hasAccess()) {
            runnable.run();
        }
    }

    private byte[] readSafeValue(Supplier<String> supplier) {
        if (hasAccess() && !EntityContext.isDevEnvironment()) {
            return supplier.get().getBytes();
        }
        return new byte[0];
    }

    private boolean hasAccess() {
        return System.currentTimeMillis() - timeSinceLastCheckPassword < TIME_REFRESH_PASSWORD || SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private String readSafeValueStr(Supplier<String> supplier) {
        return EntityContext.isDevEnvironment() ? "" : readSafeValueStrIT(supplier);
    }

    private String readSafeValueStrIT(Supplier<String> supplier) {
        if (hasAccess()) {
            return supplier.get();
        }
        return "";
    }

    private String getFeatures() {
        return entityContext.getDeviceFeatures().entrySet().stream().map(es -> es.getKey() + "_" + es.getValue()).collect(Collectors.joining(";"));
    }

    private String getWifiName() {
        return EntityContext.isDockerEnvironment() ? "" : linuxHardwareRepository.getWifiName();
    }

    @SneakyThrows
    private String getCpuTemp() {
        return EntityContext.isDockerEnvironment() ? "" : String.valueOf(SystemInfo.getCpuTemperature());
    }

    private String getKeystore() {
        return String.valueOf(user.getKeystoreDate() == null ? "" : user.getKeystoreDate().getTime());
    }
}

package org.homio.app.rest;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.model.OptionModel;
import org.homio.hquery.hardware.network.Network;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private static final String PREFIX = "13333333-3333-3333-3333-3333333330";
    private static final String WIFI_UUID = PREFIX + "10";
    private static final String DATA_UUID = PREFIX + "20";
    private static final String selectedWifiInterface = "wlan0";

    private final NetworkHardwareRepository networkHardwareRepository;
    private final MachineHardwareRepository machineHardwareRepository;

    @SneakyThrows
    @GetMapping("/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        switch (uuid) {
            case DATA_UUID -> {
                return OptionModel.key(new ObjectMapper().writeValueAsString(new MachineSummary()));
            }
            case WIFI_UUID -> {
                return OptionModel.key(readWifiList());
            }
        }
        return null;
    }

    @PutMapping("/characteristic/{uuid}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        switch (uuid) {
            case DATA_UUID -> {
                log.info("Reboot device");
                if (SystemUtils.IS_OS_LINUX) {
                    machineHardwareRepository.reboot();
                }
            }
            case WIFI_UUID -> {
                String[] split = new String(value).split("%&%");
                if (split.length == 3 && split[1].length() >= 6) {
                    if (SystemUtils.IS_OS_LINUX) {
                        log.info("Writing wifi credentials");
                        networkHardwareRepository.setWifiCredentials(split[0], split[1], split[2]);
                        networkHardwareRepository.restartNetworkInterface(selectedWifiInterface);
                    }
                }
            }
        }
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

    protected <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}

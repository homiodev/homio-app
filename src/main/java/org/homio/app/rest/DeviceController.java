package org.homio.app.rest;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.hquery.hardware.network.Network;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final EntityContext entityContext;

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

    @GetMapping("/{endpoint}")
    public @NotNull Collection<OptionModel> getDevicesWithEndpoint(@PathVariable("endpoint") @NotNull String endpoint) {
        return getDevices(device -> device.getDeviceEndpoint(endpoint) != null);
    }

    @GetMapping
    public @NotNull Collection<OptionModel> getDevices(
            @RequestParam(value = "access", defaultValue = "any") @NotNull String access,
            @RequestParam(value = "type", defaultValue = "any") @NotNull String type) {
        return getDevices(buildDeviceAccessFilter(access, type));
    }

    /**
     * Get all endpoints for specific device by ieeeAddress
     */
    @GetMapping("/endpoints")
    public @NotNull Collection<OptionModel> getEndpoints(
            @RequestParam(value = "deviceMenu", required = false) @Nullable String ieeeAddress0,
            @RequestParam(value = "deviceReadMenu", required = false) @Nullable String ieeeAddress1,
            @RequestParam(value = "deviceWriteMenu", required = false) @Nullable String ieeeAddress2,
            @RequestParam(value = "deviceWriteBoolMenu", required = false) @Nullable String ieeeAddress3,
            @RequestParam(value = "access", defaultValue = "any") @NotNull String access,
            @RequestParam(value = "type", defaultValue = "any") @NotNull String type) {
        String ieeeAddress = defaultIfEmpty(ieeeAddress0, defaultIfEmpty(ieeeAddress1, defaultIfEmpty(ieeeAddress2, ieeeAddress3)));
        DeviceEndpointsBehaviourContract entity = getDevice(ieeeAddress);
        return entity == null ? List.of() :
                entity.getDeviceEndpoints().values().stream()
                        .filter(buildEndpointAccessFilter(access))
                        .filter(buildFilterByType(type))
                        .map(this::createOptionModel)
                        .collect(Collectors.toList());
    }

    private @Nullable DeviceEndpointsBehaviourContract getDevice(String ieeeAddress) {
        if (StringUtils.isEmpty(ieeeAddress)) {
            return null;
        }
        return (DeviceEndpointsBehaviourContract)
                entityContext.findAll(DeviceBaseEntity.class)
                        .stream()
                        .filter(d -> ieeeAddress.equals(d.getIeeeAddress()))
                        .findAny().orElse(null);
    }

    private @NotNull Predicate<? super DeviceEndpoint> buildEndpointAccessFilter(@NotNull String access) {
        return switch (access) {
            case "read" -> DeviceEndpoint::isReadable;
            case "write" -> DeviceEndpoint::isWritable;
            default -> endpoint -> true;
        };
    }

    private @NotNull Predicate<? super DeviceEndpoint> buildFilterByType(@NotNull String type) {
        return (Predicate<DeviceEndpoint>) endpoint -> switch (type) {
            case "bool" -> endpoint.getEndpointType() == EndpointType.bool;
            case "number" -> endpoint.getEndpointType() == EndpointType.number;
            case "string" -> endpoint.getEndpointType() == EndpointType.string;
            default -> true;
        };
    }

    private @NotNull Predicate<DeviceEndpointsBehaviourContract> buildDeviceAccessFilter(@NotNull String access, @NotNull String type) {
        return switch (access) {
            case "read" -> device -> device.getDeviceEndpoints().values().stream()
                    .anyMatch(dv -> dv.isReadable() && filterByType(dv, type));
            case "write" -> device -> device.getDeviceEndpoints().values().stream()
                    .anyMatch(dv -> dv.isWritable() && filterByType(dv, type));
            default -> device -> true;
        };
    }

    private boolean filterByType(@NotNull DeviceEndpoint endpoint, @NotNull String type) {
        return switch (type) {
            case "bool" -> endpoint.getEndpointType() == EndpointType.bool;
            case "number" -> endpoint.getEndpointType() == EndpointType.number;
            case "string" -> endpoint.getEndpointType() == EndpointType.string;
            default -> true;
        };
    }

    private @NotNull Collection<OptionModel> getDevices(@NotNull Predicate<DeviceEndpointsBehaviourContract> deviceFilter) {
        Collection<OptionModel> list = new ArrayList<>();
        for (DeviceBaseEntity deviceEntity : entityContext.findAll(DeviceBaseEntity.class)) {
            if (deviceEntity instanceof DeviceEndpointsBehaviourContract deviceContract) {
                if (deviceFilter.test(deviceContract)) {
                    Icon icon = deviceEntity.getEntityIcon();
                    list.add(OptionModel.of(Objects.requireNonNull(deviceEntity.getIeeeAddress()), deviceContract.getDeviceFullName())
                            .setDescription(deviceContract.getDescription())
                            .setIcon(icon.getIcon())
                            .setColor(icon.getColor()));
                }
            }
        }
        return list;
    }

    private @NotNull OptionModel createOptionModel(@NotNull DeviceEndpoint endpoint) {
        return OptionModel.of(endpoint.getEndpointEntityID(), endpoint.getName(false))
                .setDescription(endpoint.getDescription())
                .setIcon(endpoint.getIcon().getIcon())
                .setColor(endpoint.getIcon().getColor());
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

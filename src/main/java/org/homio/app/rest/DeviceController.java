package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.bluetooth.BluetoothCharacteristicService;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;

@Log4j2
@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final BluetoothCharacteristicService bluetoothService;
    private final Context context;

    @SneakyThrows
    @GetMapping("/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        String characteristic = bluetoothService.getDeviceCharacteristic(uuid);
        return characteristic == null ? null : OptionModel.key(characteristic);
    }

    @SneakyThrows
    @PutMapping("/characteristic/{uuid}")
    @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        UserEntity user = context.getUser();
        if(user != null && !user.isAdmin()) {
            throw new IllegalAccessException("User is not allowed to change device characteristic");
        }
        bluetoothService.setDeviceCharacteristic(uuid, value);
    }

    @GetMapping("/{endpoint}")
    public @NotNull Collection<OptionModel> getDevicesWithEndpoint(
            @PathVariable("endpoint") @NotNull String endpoint,
            @RequestParam("prefix") @NotNull String prefix) {
        return getDevices(prefix, device -> device.getDeviceEndpoint(endpoint) != null);
    }

    @GetMapping
    public @NotNull Collection<OptionModel> getDevices(
            @RequestParam(value = "access", defaultValue = "any") @NotNull String access,
            @RequestParam(value = "type", defaultValue = "any") @NotNull String type,
            @RequestParam("prefix") @NotNull String prefix) {
        return getDevices(prefix, buildDeviceAccessFilter(access, type));
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
                context.db().findAll(DeviceBaseEntity.class)
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

    private @NotNull Collection<OptionModel> getDevices(
            @NotNull String prefix,
            @NotNull Predicate<DeviceEndpointsBehaviourContract> deviceFilter) {

        Collection<OptionModel> list = new ArrayList<>();
        for (DeviceBaseEntity deviceEntity : context.db().findAll(DeviceBaseEntity.class)) {
            if (deviceEntity instanceof DeviceEndpointsBehaviourContract deviceContract) {
                if (deviceEntity.getEntityID().startsWith("dvc_" + prefix) && deviceFilter.test(deviceContract)) {
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
}

package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.homio.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RestController
@RequestMapping("/rest/zigbee")
@RequiredArgsConstructor
public class ZigBeeController {

    private final EntityContext entityContext;

    @GetMapping("/device/{endpoint}")
    public @NotNull Collection<OptionModel> getDevicesWithEndpoint(@PathVariable("endpoint") @NotNull String endpoint) {
        return getDevices(device -> device.getDeviceEndpoint(endpoint) != null);
    }

    @GetMapping("/device")
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
        return getZigBeeCoordinators().stream()
                .map(c -> c.getZigBeeDevice(ieeeAddress))
                .filter(Objects::nonNull)
                .flatMap(d -> ((Map<String, DeviceEndpoint>) d.getEndpoints()).values().stream())
                .filter(buildEndpointAccessFilter(access))
                .filter(buildFilterByType(type))
                .map(this::createOptionModel)
                .collect(Collectors.toList());
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

    private @NotNull Predicate<ZigBeeDeviceBaseEntity> buildDeviceAccessFilter(@NotNull String access, @NotNull String type) {
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

    private @NotNull Collection<OptionModel> getDevices(@NotNull Predicate<ZigBeeDeviceBaseEntity> deviceFilter) {
        Collection<OptionModel> list = new ArrayList<>();
        for (ZigBeeBaseCoordinatorEntity coordinator : getZigBeeCoordinators()) {
            Collection<ZigBeeDeviceBaseEntity> devices = coordinator.getZigBeeDevices();
            for (ZigBeeDeviceBaseEntity zigBeeDevice : devices) {
                if (deviceFilter.test(zigBeeDevice)) {
                    Icon icon = Objects.requireNonNull(zigBeeDevice.getEntityIcon());
                    list.add(OptionModel.of(Objects.requireNonNull(zigBeeDevice.getIeeeAddress()), zigBeeDevice.getDeviceFullName())
                                        .setDescription(zigBeeDevice.getDescription())
                                        .setIcon(icon.getIcon())
                                        .setColor(icon.getColor()));
                }
            }
        }
        return list;
    }

    private @NotNull Collection<ZigBeeBaseCoordinatorEntity> getZigBeeCoordinators() {
        return entityContext.findAll(MicroControllerBaseEntity.class)
                            .stream().filter(m -> m instanceof ZigBeeBaseCoordinatorEntity)
                            .map(d -> (ZigBeeBaseCoordinatorEntity) d)
                            .collect(Collectors.toList());
    }

    private @NotNull OptionModel createOptionModel(@NotNull DeviceEndpoint endpoint) {
        return OptionModel.of(endpoint.getEndpointEntityID(), endpoint.getName(false))
                .setDescription(endpoint.getDescription())
                .setIcon(endpoint.getIcon().getIcon())
                .setColor(endpoint.getIcon().getColor());
    }
}

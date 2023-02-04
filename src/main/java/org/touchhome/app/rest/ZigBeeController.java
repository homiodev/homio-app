package org.touchhome.app.rest;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.types.MicroControllerBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeBaseCoordinatorEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeProperty;
import org.touchhome.bundle.api.entity.zigbee.ZigBeeProperty.PropertyType;
import org.touchhome.bundle.api.model.OptionModel;

@Log4j2
@RestController
@RequestMapping("/rest/zigbee")
@RequiredArgsConstructor
public class ZigBeeController {

    private final EntityContext entityContext;

    @GetMapping("/device/{propertyName}")
    public Collection<OptionModel> getDevicesWithProperty(@PathVariable("propertyName") String propertyName) {
        return getDevices(device -> device.getProperty(propertyName) != null);
    }

    @GetMapping("/device")
    public Collection<OptionModel> getDevices(
        @RequestParam(value = "access", required = false) String access,
        @RequestParam(value = "type", required = false) String type) {
        return getDevices(buildDeviceAccessFilter(access, type));
    }

    /**
     * Get all properties for specific device by ieeeAddress
     */
    @GetMapping("/property")
    public Collection<OptionModel> getProperties(
        @RequestParam(value = "deviceMenu", required = false) String ieeeAddress0,
        @RequestParam(value = "deviceReadMenu", required = false) String ieeeAddress1,
        @RequestParam(value = "deviceWriteMenu", required = false) String ieeeAddress2,
        @RequestParam(value = "deviceWriteBoolMenu", required = false) String ieeeAddress3,
        @RequestParam(value = "access", required = false) String access,
        @RequestParam(value = "type", required = false) String type) {
        String ieeeAddress = defaultIfEmpty(ieeeAddress0, defaultIfEmpty(ieeeAddress1, defaultIfEmpty(ieeeAddress2, ieeeAddress3)));
        return getZigBeeCoordinators().stream()
                                      .map(c -> c.getZigBeeDevice(ieeeAddress))
                                      .filter(Objects::nonNull)
                                      .flatMap(d -> ((Map<String, ZigBeeProperty>) d.getProperties()).values().stream())
                                      .filter(buildPropertyAccessFilter(access))
                                      .map(this::createOptionModel)
                                      .collect(Collectors.toList());
    }

    private @NotNull Predicate<? super ZigBeeProperty> buildPropertyAccessFilter(String access) {
        switch (access) {
            case "read":
                return ZigBeeProperty::isReadable;
            case "write":
                return ZigBeeProperty::isWritable;
        }
        return zigBeeProperty -> true;
    }

    private @NotNull Predicate<ZigBeeDeviceBaseEntity> buildDeviceAccessFilter(String access, String type) {
        switch (access) {
            case "read":
                return device -> device.getProperties().values().stream()
                                       .anyMatch(p -> ((ZigBeeProperty) p).isReadable() && filterByType((ZigBeeProperty) p, type));
            case "write":
                return device -> device.getProperties().values().stream()
                                       .anyMatch(p -> ((ZigBeeProperty) p).isWritable() && filterByType((ZigBeeProperty) p, type));
        }
        return device -> true;
    }

    private boolean filterByType(ZigBeeProperty zigBeeProperty, String type) {
        switch (type) {
            case "bool":
                return zigBeeProperty.getPropertyType() == PropertyType.bool;
            case "number":
                return zigBeeProperty.getPropertyType() == PropertyType.number;
            case "string":
                return zigBeeProperty.getPropertyType() == PropertyType.string;
        }
        return true;
    }

    private @NotNull Collection<OptionModel> getDevices(Predicate<ZigBeeDeviceBaseEntity> deviceFilter) {
        Collection<OptionModel> list = new ArrayList<>();
        for (ZigBeeBaseCoordinatorEntity coordinator : getZigBeeCoordinators()) {
            Collection<ZigBeeDeviceBaseEntity> devices = coordinator.getZigBeeDevices();
            for (ZigBeeDeviceBaseEntity zigBeeDevice : devices) {
                if (deviceFilter.test(zigBeeDevice)) {
                    list.add(OptionModel.of(zigBeeDevice.getIeeeAddress(), zigBeeDevice.getDeviceFullName())
                                        .setDescription(zigBeeDevice.getDescription())
                                        .setIcon(zigBeeDevice.getIcon())
                                        .setColor(zigBeeDevice.getIconColor()));
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

    private @NotNull OptionModel createOptionModel(ZigBeeProperty property) {
        return OptionModel.of(property.getKey(), property.getName()).setDescription(property.getDescription())
                          .setIcon(property.getIcon()).setColor(property.getIconColor());
    }
}

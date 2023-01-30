package org.touchhome.app.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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
import org.touchhome.bundle.api.model.OptionModel;

@Log4j2
@RestController
@RequestMapping("/rest/zigbee")
@RequiredArgsConstructor
public class ZigBeeController {

    private final EntityContext entityContext;

    @GetMapping("/property/{name}")
    public Collection<OptionModel> getPropertyDevices(@PathVariable("name") String propertyName,
        @RequestParam(value = "writable", defaultValue = "false") boolean writable) {
        return findAllDevicesByProperty(propertyName, writable);
    }

    @GetMapping("/property")
    public Collection<OptionModel> getDeviceProperties(@RequestParam(value = "deviceMenu", required = false) String ieeeAddress) {
        Collection<OptionModel> list = new ArrayList<>();
        for (ZigBeeBaseCoordinatorEntity zigBeeCoordinator : getZigBeeCoordinators()) {
            Map<String, Map<String, ZigBeeProperty>> properties = zigBeeCoordinator.getAllProperties();
            if (properties.containsKey(ieeeAddress)) {
                for (ZigBeeProperty property : properties.get(ieeeAddress).values()) {
                    list.add(createOptionModel(property));
                }
            }
        }
        return list;
    }

    @GetMapping("/device")
    public Collection<OptionModel> getPropertyDevices() {
        Collection<OptionModel> list = new ArrayList<>();
        for (ZigBeeBaseCoordinatorEntity zigBeeCoordinator : getZigBeeCoordinators()) {
            Collection<ZigBeeDeviceBaseEntity> zigBeeDevices = (zigBeeCoordinator).getZigBeeDevices();
            for (ZigBeeDeviceBaseEntity zigBeeDevice : zigBeeDevices) {
                list.add(OptionModel.of(zigBeeDevice.getIeeeAddress(), zigBeeDevice.getDeviceFullName())
                                    .setDescription(zigBeeDevice.getDescription())
                                    .setIcon(zigBeeDevice.getIcon())
                                    .setColor(zigBeeDevice.getIconColor()));
            }
        }
        return list;
    }

    private Collection<ZigBeeBaseCoordinatorEntity> getZigBeeCoordinators() {
        Collection<ZigBeeBaseCoordinatorEntity> list = new ArrayList<>();

        for (MicroControllerBaseEntity microController : entityContext.findAll(MicroControllerBaseEntity.class)) {
            if (microController instanceof ZigBeeBaseCoordinatorEntity) {
                list.add((ZigBeeBaseCoordinatorEntity) microController);
            }
        }
        return list;
    }

    private Collection<OptionModel> findAllDevicesByProperty(String propertyName, boolean writable) {
        Collection<OptionModel> list = new ArrayList<>();
        for (MicroControllerBaseEntity microController : entityContext.findAll(MicroControllerBaseEntity.class)) {
            if (microController instanceof ZigBeeBaseCoordinatorEntity) {
                Map<String, Map<String, ZigBeeProperty>> properties = ((ZigBeeBaseCoordinatorEntity) microController).getAllProperties();
                list.addAll(filterProperties(properties, propertyName, writable));
            }
        }
        return list;
    }

    private Collection<OptionModel> filterProperties(Map<String, Map<String, ZigBeeProperty>> properties, String propertyName, boolean writable) {
        Stream<ZigBeeProperty> stream = properties.values().stream().flatMap(dp -> dp.values().stream());
        // filter by property name
        stream = stream.filter(p -> p.getName().equals(propertyName));
        // filter by writable if require
        if (writable) {
            stream = stream.filter(ZigBeeProperty::isWritable);
        }
        return stream.map(this::createOptionModel).collect(Collectors.toList());
    }

    private OptionModel createOptionModel(ZigBeeProperty property) {
        return OptionModel.of(property.getKey(), property.getName()).setDescription(property.getDescription())
                          .setIcon(property.getIcon()).setColor(property.getIconColor());
    }

    /*@GetMapping("/option/zcl/{clusterId}")
    public Collection<OptionModel> filterByClusterId(
        @PathVariable("clusterId") int clusterId,
        @RequestParam(value = "includeClusterName", required = false)
        boolean includeClusterName) {
        return filterByClusterIdAndEndpointCount(clusterId, null, includeClusterName);
    }*/

    /*@GetMapping("/option/clusterName/{clusterName}")
    public Collection<OptionModel> filterByClusterName(
        @PathVariable("clusterName") String clusterName,
        @RequestParam(value = "includeClusterName", required = false)
        boolean includeClusterName) {
        List<OptionModel> list = new ArrayList<>();
        *//*for (ZigbeeCoordinatorEntity coordinator :
                entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity device : coordinator.getOnlineDevices()) {
                ZigBeeEndpointEntity endpoint =
                        device.getEndpoints().stream()
                                .filter(f -> f.getName().equals(clusterName))
                                .findAny()
                                .orElse(null);

                // add zigBeeDevice
                if (endpoint != null) {
                    String key =
                            coordinator.getEntityID()
                                    + ":"
                                    + device.getIeeeAddress()
                                    + (includeClusterName ? "/" + endpoint.getName() : "");
                    list.add(
                            OptionModel.of(
                                    key, endpoint.getDescription() + " - " + device.getTitle()));
                }
            }
        }*//*
        return list;
    }*/

    /*@GetMapping("/option/alarm")
    public Collection<OptionModel> getAlarmSensors() {
        return null; // return filterByClusterId(ZclIasZoneCluster.CLUSTER_ID, true);
    }*/

    /*@GetMapping("/option/buttons")
    public Collection<OptionModel> getButtons() {
       *//* Collection<OptionModel> options =
                filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 1, false);
        options.addAll(filterByModelIdentifier("lumi.remote"));
        return options;*//*
        return null;
    }*/

    /*@GetMapping("/option/doubleButtons")
    public Collection<OptionModel> getDoubleButtons() {
        return null;//  return filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 2, false);
    }*/

    /*@GetMapping("/option/model/{modelIdentifier}")
    public Collection<OptionModel> filterByModelIdentifier(@PathVariable("modelIdentifier") String modelIdentifier) {
        List<OptionModel> list = new ArrayList<>();
        *//*for (ZigbeeCoordinatorEntity coordinator :
            entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity zigBeeDevice : coordinator.getOnlineDevices()) {
                String deviceMI = zigBeeDevice.getModelIdentifier();
                if (deviceMI != null && deviceMI.startsWith(modelIdentifier)) {
                    list.add(
                        OptionModel.of(
                            coordinator.getEntityID() + ":" + zigBeeDevice.getIeeeAddress(),
                            zigBeeDevice.getTitle()));
                }
            }
        }*//*

        return list;
    }*/

    /*private Collection<OptionModel> filterByClusterIdAndEndpointCount(
        Integer clusterId, Integer endpointCount, boolean includeClusterName) {
        List<OptionModel> list = new ArrayList<>();
        *//*for (ZigbeeCoordinatorEntity coordinator :
            entityContext.findAll(ZigbeeCoordinatorEntity.class)) {
            for (ZigBeeDeviceEntity device : coordinator.getOnlineDevices()) {
                List<ZigBeeEndpointEntity> endpoints =
                    device.getEndpoints().stream()
                          .filter(e -> containsAny(e.getService().getCluster(), clusterId))
                          .collect(Collectors.toList());

                if (!endpoints.isEmpty()) {
                    if (endpointCount == null || endpointCount == endpoints.size()) {
                        String key =
                            coordinator.getEntityID()
                                + ":"
                                + device.getIeeeAddress()
                                + (includeClusterName
                                ? "/" + endpoints.iterator().next().getName()
                                : "");
                        list.add(OptionModel.of(key, device.getTitle()));
                    }
                }
            }
        }*//*
        return list;
    }*/
}

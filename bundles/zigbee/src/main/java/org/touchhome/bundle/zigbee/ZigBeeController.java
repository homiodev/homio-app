package org.touchhome.bundle.zigbee;

import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/rest/zigbee")
@RequiredArgsConstructor
public class ZigBeeController {
    private final EntityContext entityContext;
    private final ZigBeeBundleContext zigbeeBundleContext;

    @GetMapping("option/zcl/{clusterId}")
    public List<Option> filterByClusterId(@PathVariable("clusterId") int clusterId,
                                          @RequestParam(value = "includeClusterName", required = false) boolean includeClusterName) {
        return filterByClusterIdAndEndpointCount(clusterId, null, includeClusterName);
    }

    @GetMapping("option/clusterName/{clusterName}")
    public List<Option> filterByClusterName(@PathVariable("clusterName") String clusterName,
                                            @RequestParam(value = "includeClusterName", required = false) boolean includeClusterName) {
        List<Option> list = new ArrayList<>();
        for (ZigBeeDevice zigBeeDevice : zigbeeBundleContext.getCoordinatorHandlers().getZigBeeDevices().values()) {
            ZigBeeConverterEndpoint zigBeeConverterEndpoint = zigBeeDevice.getZigBeeConverterEndpoints().keySet()
                    .stream().filter(f -> f.getClusterName().equals(clusterName)).findAny().orElse(null);
            // add zigBeeDevice
            if (zigBeeConverterEndpoint != null) {
                String key = zigBeeDevice.getNodeIeeeAddress() + (includeClusterName ? "/" + zigBeeConverterEndpoint.getClusterName() : "");
                list.add(Option.of(key, zigBeeConverterEndpoint.getClusterDescription() + " - " +
                        entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + zigBeeDevice.getNodeIeeeAddress()).getTitle()));
            }
        }
        return list;
    }

    private List<Option> filterByClusterIdAndEndpointCount(Integer clusterId, Integer endpointCount, boolean includeClusterName) {
        List<Option> list = new ArrayList<>();
        for (ZigBeeDevice zigBeeDevice : zigbeeBundleContext.getCoordinatorHandlers().getZigBeeDevices().values()) {
            List<ZigBeeConverterEndpoint> endpoints = getZigBeeConverterEndpointsByClusterId(zigBeeDevice, clusterId);

            if (!endpoints.isEmpty()) {
                if (endpointCount == null || endpointCount == endpoints.size()) {
                    String key = zigBeeDevice.getNodeIeeeAddress() + (includeClusterName ? "/" + endpoints.iterator().next().getClusterName() : "");
                    list.add(Option.of(key, entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + zigBeeDevice.getNodeIeeeAddress()).getTitle()));
                }
            }
        }
        return list;
    }

    @GetMapping("option/alarm")
    public List<Option> getAlarmSensors() {
        return filterByClusterId(ZclIasZoneCluster.CLUSTER_ID, true);
    }

    @GetMapping("option/buttons")
    public List<Option> getButtons() {
        List<Option> options = filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 1, false);
        options.addAll(filterByModelIdentifier("lumi.remote"));
        return options;
    }

    @GetMapping("option/doubleButtons")
    public List<Option> getDoubleButtons() {
        return filterByClusterIdAndEndpointCount(ZclOnOffCluster.CLUSTER_ID, 2, false);
    }

    @GetMapping("option/model/{modelIdentifier}")
    public List<Option> filterByModelIdentifier(@PathVariable("modelIdentifier") String modelIdentifier) {
        List<Option> list = new ArrayList<>();
        for (ZigBeeDevice zigBeeDevice : zigbeeBundleContext.getCoordinatorHandlers().getZigBeeDevices().values()) {
            String deviceMI = zigBeeDevice.getZigBeeNodeDescription().getModelIdentifier();
            if (deviceMI != null && deviceMI.startsWith(modelIdentifier)) {
                list.add(Option.of(zigBeeDevice.getZigBeeNodeDescription().getIeeeAddress(),
                        entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + zigBeeDevice.getNodeIeeeAddress()).getTitle()));
            }
        }

        return list;
    }

    private List<ZigBeeConverterEndpoint> getZigBeeConverterEndpointsByClusterId(ZigBeeDevice zigBeeDevice, Integer clusterId) {
        List<ZigBeeConverterEndpoint> endpoints = new ArrayList<>();
        for (ZigBeeConverterEndpoint zigBeeConverterEndpoint : zigBeeDevice.getZigBeeConverterEndpoints().keySet()) {
            if (TouchHomeUtils.containsAny(zigBeeConverterEndpoint.getZigBeeConverter().clientClusters(), clusterId)) {
                endpoints.add(zigBeeConverterEndpoint);
            }
        }
        return endpoints;
    }
}

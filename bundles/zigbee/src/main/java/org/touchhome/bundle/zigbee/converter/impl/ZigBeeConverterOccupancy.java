package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOccupancySensingCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the occupancy sensor.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:sensor_occupancy", description = "Occupancy level", linkType = DeviceChannelLinkType.Boolean, clientClusters = {ZclOccupancySensingCluster.CLUSTER_ID})
public class ZigBeeConverterOccupancy extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclOccupancySensingCluster clusterOccupancy;

    @Override
    public boolean initializeDevice() {
        log.debug("{}: Initialising device occupancy cluster", endpoint.getIeeeAddress());

        ZclOccupancySensingCluster serverClusterOccupancy = (ZclOccupancySensingCluster) endpoint
                .getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID);
        if (serverClusterOccupancy == null) {
            log.error("{}: Error opening occupancy cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterOccupancy).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = serverClusterOccupancy
                        .setOccupancyReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        clusterOccupancy = (ZclOccupancySensingCluster) endpoint.getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID);
        if (clusterOccupancy == null) {
            log.error("{}: Error opening occupancy cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        clusterOccupancy.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}: Closing device occupancy cluster", endpoint.getIeeeAddress());

        clusterOccupancy.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        clusterOccupancy.getOccupancy(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclOccupancySensingCluster.CLUSTER_ID) == null) {
            log.trace("{}: Occupancy sensing cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.OCCUPANCY_SENSING
                && attribute.getId() == ZclOccupancySensingCluster.ATTR_OCCUPANCY) {
            Integer value = (Integer) val;
            if (value != null && value == 1) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}

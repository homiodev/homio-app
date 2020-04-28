package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclRelativeHumidityMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

/**
 * Converter for the relative humidity channel
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:measurement_relativehumidity", description = "Relative humidity", clientClusters = {ZclRelativeHumidityMeasurementCluster.CLUSTER_ID}, linkType = DeviceChannelLinkType.Float)
public class ZigBeeConverterRelativeHumidity extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclRelativeHumidityMeasurementCluster cluster;

    @Override
    public boolean initializeDevice() {
        pollingPeriod = REPORTING_PERIOD_DEFAULT_MAX;
        ZclRelativeHumidityMeasurementCluster serverCluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult response = serverCluster.setMeasuredValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1)
                        .get();
                handleReportingResponse(response, REPORTING_PERIOD_DEFAULT_MAX, REPORTING_PERIOD_DEFAULT_MAX);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            pollingPeriod = POLLING_PERIOD_HIGH;
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclRelativeHumidityMeasurementCluster) endpoint
                .getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device relative humidity measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        cluster.getMeasuredValue(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclRelativeHumidityMeasurementCluster.CLUSTER_ID) == null) {
            log.trace("{}/{}: Relative humidity cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.RELATIVE_HUMIDITY_MEASUREMENT
                && attribute.getId() == ZclRelativeHumidityMeasurementCluster.ATTR_MEASUREDVALUE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(BigDecimal.valueOf(value, 2)));
        }
    }
}

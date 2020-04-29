package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIlluminanceMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the illuminance channel
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:measurement_illuminance", description = "Illuminance level", linkType = DeviceChannelLinkType.Float,
        clientClusters = {ZclIlluminanceMeasurementCluster.CLUSTER_ID})
public class ZigBeeConverterIlluminance extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

   /* private static BigDecimal CHANGE_DEFAULT = new BigDecimal(5000);
    private static BigDecimal CHANGE_MIN = new BigDecimal(10);
    private static BigDecimal CHANGE_MAX = new BigDecimal(20000);*/

    private ZclIlluminanceMeasurementCluster cluster;
    private ZclAttribute attribute;

    @Override
    public boolean initializeDevice() {
        pollingPeriod = REPORTING_PERIOD_DEFAULT_MAX;
        ZclIlluminanceMeasurementCluster serverCluster = (ZclIlluminanceMeasurementCluster) endpoint
                .getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device illuminance measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                updateServerPoolingPeriod(serverCluster, ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE, false, 10);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.debug("{}/{}: Exception configuring measured value reporting", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclIlluminanceMeasurementCluster) endpoint.getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device illuminance measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        attribute = cluster.getAttribute(ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE);
        if (attribute == null) {
            log.error("{}/{}: Error opening device illuminance measurement attribute", endpoint.getIeeeAddress(), endpoint.getEndpointId());
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
    public int getPollingPeriod() {
        return zigBeeDevice.getZigBeeDeviceEntity().getPoolingPeriod();
    }

    @Override
    protected void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIlluminanceMeasurementCluster.CLUSTER_ID) == null) {
            log.trace("{}/{}: Illuminance measurement cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }
        return true;
    }

    @Override
    public void updateConfiguration() {
        try {
            updateServerPoolingPeriod(cluster, ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE, true, 10);
        } catch (InterruptedException | ExecutionException e) {
            log.debug("{}: Illuminance measurement exception setting reporting", endpoint.getIeeeAddress(), e);
        }
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.ILLUMINANCE_MEASUREMENT
                && attribute.getId() == ZclIlluminanceMeasurementCluster.ATTR_MEASUREDVALUE) {
            updateChannelState(new DecimalType((int) val));
        }
    }
}

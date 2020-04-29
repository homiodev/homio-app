package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclTemperatureMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the temperature channel
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:measurement_temperature", description = "Temperature",
        linkType = DeviceChannelLinkType.Float,
        serverClusters = {ZclTemperatureMeasurementCluster.CLUSTER_ID},
        clientClusters = {ZclTemperatureMeasurementCluster.CLUSTER_ID})
public class ZigBeeConverterTemperature extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclTemperatureMeasurementCluster cluster;
    private ZclTemperatureMeasurementCluster clusterClient;
    private ZclAttribute attribute;
    private ZclAttribute attributeClient;

    @Override
    public boolean initializeDevice() {
        pollingPeriod = REPORTING_PERIOD_DEFAULT_MAX;
        ZclTemperatureMeasurementCluster clientCluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (clientCluster == null) {
            // Nothing to do, but we still return success
            return true;
        }

        ZclTemperatureMeasurementCluster serverCluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device temperature measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster
                        .getAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
                handleReportingResponse(reportingResponse);
            } else {
                log.debug("{}/{}: Failed to bind temperature measurement cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclTemperatureMeasurementCluster) endpoint
                .getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
        if (cluster != null) {
            attribute = cluster.getAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
            // Add a listener
            cluster.addAttributeListener(this);
        } else {
            clusterClient = (ZclTemperatureMeasurementCluster) endpoint
                    .getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID);
            attributeClient = clusterClient.getLocalAttribute(ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE);
            attributeClient.setImplemented(true);
        }

        if (cluster == null && clusterClient == null) {
            log.error("{}/{}: Error opening device temperature measurement cluster", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        if (cluster != null) {
            cluster.removeAttributeListener(this);
        }
    }

    @Override
    protected void handleRefresh() {
        if (attribute != null) {
            attribute.readValue(0);
        }
    }

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        if (attributeClient == null) {
            log.warn("{}/{}: Temperature measurement update but remote client not set", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        Integer value = temperatureToValue(command);

        if (value == null) {
            log.warn("{}/{}: Temperature measurement update {} [{}] was not processed", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName());
            return;
        }

        attributeClient.setValue(value);
        attributeClient.reportValue(value);
    }*/

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getOutputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null
                && endpoint.getInputCluster(ZclTemperatureMeasurementCluster.CLUSTER_ID) == null) {
            log.trace("{}/{}: Temperature measurement cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.TEMPERATURE_MEASUREMENT
                && attribute.getId() == ZclTemperatureMeasurementCluster.ATTR_MEASUREDVALUE) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }
}

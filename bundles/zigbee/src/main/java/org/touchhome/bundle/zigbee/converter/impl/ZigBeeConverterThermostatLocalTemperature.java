package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the thermostat local temperature channel
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:thermostat_localtemp",
        clientClusters = {ZclThermostatCluster.CLUSTER_ID})
public class ZigBeeConverterThermostatLocalTemperature extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {

    private final int INVALID_TEMPERATURE = 0x8000;

    private ZclThermostatCluster cluster;

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                CommandResult reportingResponse = serverCluster
                        .setLocalTemperatureReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1)
                        .get();
                handleReportingResponse(reportingResponse);
            } else {
                log.debug("{}/{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
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
        cluster.getLocalTemperature(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            log.trace("{}/{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        try {
            if (!cluster.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = cluster.getLocalTemperature(Long.MAX_VALUE);
                if (capabilities == null) {
                    log.trace("{}/{}: Thermostat local temperature returned null", endpoint.getIeeeAddress());
                    return false;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_LOCALTEMPERATURE)) {
                log.trace("{}/{}: Thermostat local temperature not supported", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}/{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_LOCALTEMPERATURE) {
            Integer value = (Integer) val;
            if (value != null && value != INVALID_TEMPERATURE) {
                updateChannelState(valueToTemperature(value));
            }
        }
    }
}

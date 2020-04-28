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
 * Converter for the thermostat unoccupied cooling setpoint channel
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:thermostat_unoccupiedcooling", clientClusters = {ZclThermostatCluster.CLUSTER_ID})
public class ZigBeeConverterThermostatUnoccupiedCooling extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            // Configure reporting
            ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_UNOCCUPIEDCOOLINGSETPOINT);
            CommandResult reportingResponse = attribute
                    .setReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
            handleReportingResponse(reportingResponse);
            if (!bindResponse.isSuccess()) {
            } else {
                log.debug("{}/{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_UNOCCUPIEDCOOLINGSETPOINT);
        if (attribute == null) {
            log.error("{}/{}: Error opening device thermostat unoccupied cooling setpoint attribute",
                    endpoint.getIeeeAddress(), endpoint.getEndpointId());
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

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        Integer value = temperatureToValue(command);

        if (value == null) {
            log.warn("{}: Thermostat unoccupied cooling setpoint {} [{}] was not processed",
                    endpoint.getIeeeAddress(), command, command.getClass().getSimpleName());
            return;
        }

        attribute.writeValue(value);
    }*/

    @Override
    protected void handleRefresh() {
        attribute.readValue(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclThermostatCluster cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            log.trace("{}/{}: Thermostat cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            if (!cluster.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = cluster.getUnoccupiedCoolingSetpoint(Long.MAX_VALUE);
                if (capabilities == null) {
                    log.trace("{}/{}: Thermostat unoccupied cooling setpoint returned null", endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return false;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_UNOCCUPIEDCOOLINGSETPOINT)) {
                log.trace("{}/{}: Thermostat unoccupied cooling setpoint not supported", endpoint.getIeeeAddress(), endpoint.getEndpointId());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_UNOCCUPIEDCOOLINGSETPOINT) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }
}

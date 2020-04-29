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
 * Converter for the thermostat occupied cooling setpoint channel. This specifies the cooling mode setpoint when the
 * room is occupied. It shall be set to a value in the range defined by the MinCoolSetpointLimit and
 * MaxCoolSetpointLimit attributes.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:thermostat_occupiedcooling",
        clientClusters = {ZclThermostatCluster.CLUSTER_ID})
public class ZigBeeConverterThermostatOccupiedCooling extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener {

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

    @Override
    public boolean initializeDevice() {
        pollingPeriod = REPORTING_PERIOD_DEFAULT_MAX;
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDCOOLINGSETPOINT);
                CommandResult reportingResponse = attribute
                        .setReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
                handleReportingResponse(reportingResponse);
            } else {
                log.debug("{}/{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
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

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_OCCUPIEDCOOLINGSETPOINT);
        if (attribute == null) {
            log.error("{}: Error opening device thermostat occupied cooling setpoint attribute",
                    endpoint.getIeeeAddress());
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
            log.warn("{}: Thermostat occupied cooling setpoint {} [{}] was not processed", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                    command, command.getClass().getSimpleName());
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
                Integer capabilities = cluster.getOccupiedCoolingSetpoint(Long.MAX_VALUE);
                if (capabilities == null) {
                    log.trace("{}/{}: Thermostat occupied cooling setpoint returned null", endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return false;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_OCCUPIEDCOOLINGSETPOINT)) {
                log.trace("{}/{}: Thermostat occupied cooling setpoint not supported", endpoint.getIeeeAddress(), endpoint.getEndpointId());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}/{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_OCCUPIEDCOOLINGSETPOINT) {
            updateChannelState(valueToTemperature((Integer) val));
        }
    }
}

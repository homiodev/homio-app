package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclThermostatCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the thermostat running mode channel. This is a read-only channel the presents the current state of the
 * thermostat.
 * <p>
 * ThermostatRunningMode represents the running mode of the thermostat. The thermostat running mode can
 * only be Off, Cool or Heat. This attribute is intended to provide additional information when the thermostat’s
 * system mode is in auto mode. The attribute value is maintained to have the same value as the SystemMode
 * attribute.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:thermostat_runningmode",
        clientClusters = {ZclThermostatCluster.CLUSTER_ID})
public class ZigBeeConverterThermostatRunningMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclThermostatCluster cluster;

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            pollingPeriod = POLLING_PERIOD_HIGH;
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE);
                CommandResult reportingResponse = attribute
                        .setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse);
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
        cluster.read(cluster.getAttribute(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE));
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
                Integer capabilities = cluster.getThermostatRunningMode(Long.MAX_VALUE);
                if (capabilities == null) {
                    log.trace("{}/{}: Thermostat running mode returned null", endpoint.getIeeeAddress(), endpoint.getEndpointId());
                    return false;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE)) {
                log.trace("{}/{}: Thermostat running mode not supported", endpoint.getIeeeAddress(), endpoint.getEndpointId());
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
                && attribute.getId() == ZclThermostatCluster.ATTR_THERMOSTATRUNNINGMODE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(value));
        }
    }
}

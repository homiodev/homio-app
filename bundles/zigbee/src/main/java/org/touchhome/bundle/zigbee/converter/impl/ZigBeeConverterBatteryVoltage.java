package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.QuantityType;
import tec.uom.se.unit.Units;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

/**
 * Converter for the battery voltage channel.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:battery_voltage", clientClusters = {ZclPowerConfigurationCluster.CLUSTER_ID})
public class ZigBeeConverterBatteryVoltage extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeDevice() {
        log.debug("{}/{}: Initialising device battery voltage converter", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        ZclPowerConfigurationCluster serverCluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening power configuration cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per ten minutes - no slower than every 2 hours.
                CommandResult reportingResponse = serverCluster.setReporting(ZclPowerConfigurationCluster.ATTR_BATTERYVOLTAGE,
                        600, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                handleReportingResponseHight(reportingResponse);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening power configuration cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing power configuration cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        cluster.getBatteryVoltage(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclPowerConfigurationCluster powerCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (powerCluster == null) {
            log.trace("{}: Power configuration cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        try {
            if (!powerCluster.discoverAttributes(false).get()
                    && !powerCluster.isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYVOLTAGE)) {
                log.trace("{}: Power configuration cluster battery voltage not supported",
                        endpoint.getIeeeAddress());

                return false;
            } else if (powerCluster.getBatteryVoltage(Long.MAX_VALUE) == null) {
                log.trace("{}: Power configuration cluster battery voltage returned null",
                        endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}/{}: Exception discovering attributes in power configuration cluster",
                    endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYVOLTAGE) {
            Integer value = (Integer) val;
            if (value == 0xFF) {
                // The value 0xFF indicates an invalid or unknown reading.
                return;
            }
            BigDecimal valueInVolt = BigDecimal.valueOf(value, 1);
            updateChannelState(new QuantityType<>(valueInVolt, Units.VOLT));
        }
    }
}

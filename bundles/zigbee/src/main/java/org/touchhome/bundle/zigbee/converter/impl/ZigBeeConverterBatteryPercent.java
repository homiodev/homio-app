package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.util.concurrent.ExecutionException;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING;

/**
 * Converter for the battery percent channel.
 */
@Log4j2
@ZigBeeConverter(name = "system:battery-level", clientClusters = {ZclPowerConfigurationCluster.CLUSTER_ID})
public class ZigBeeConverterBatteryPercent extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeDevice() {
        log.debug("{}: Initialising device battery percent converter", endpoint.getIeeeAddress());

        ZclPowerConfigurationCluster serverCluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per ten minutes - no slower than every 2 hours.
                handleReportingResponseHight(serverCluster.setReporting(ATTR_BATTERYPERCENTAGEREMAINING, 600, REPORTING_PERIOD_DEFAULT_MAX, 1).get());
            } else {
                log.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(), Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}: Error opening power configuration cluster", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listener, then request the status
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}: Closing power configuration cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        cluster.getBatteryPercentageRemaining(0);
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
            if (!powerCluster.discoverAttributes(false).get() && !powerCluster
                    .isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING)) {
                log.trace("{}: Power configuration cluster battery percentage not supported",
                        endpoint.getIeeeAddress());

                return false;
            } else if (powerCluster.getBatteryPercentageRemaining(Long.MAX_VALUE) == null) {
                log.trace("{}: Power configuration cluster battery percentage returned null",
                        endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}: Exception discovering attributes in power configuration cluster",
                    endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION
                && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYPERCENTAGEREMAINING) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(value / 2));
        }
    }
}

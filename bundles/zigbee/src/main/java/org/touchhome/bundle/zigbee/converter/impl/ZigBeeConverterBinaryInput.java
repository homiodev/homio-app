package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBinaryInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.ExecutionException;

/**
 * Converter for the binary input sensor.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:binaryinput", clientClusters = {ZclBinaryInputBasicCluster.CLUSTER_ID})
public class ZigBeeConverterBinaryInput extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclBinaryInputBasicCluster binaryInputCluster;

    @Override
    public boolean initializeDevice() {
        log.debug("{}: Initialising device binary input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        ZclBinaryInputBasicCluster binaryInputCluster = (ZclBinaryInputBasicCluster) endpoint.getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID);
        if (binaryInputCluster == null) {
            log.error("{}: Error opening binary input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(binaryInputCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = binaryInputCluster.setPresentValueReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        binaryInputCluster = (ZclBinaryInputBasicCluster) endpoint
                .getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID);
        if (binaryInputCluster == null) {
            log.error("{}: Error opening binary input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        binaryInputCluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}: Closing device binary input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
        binaryInputCluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        binaryInputCluster.getPresentValue(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclBinaryInputBasicCluster.CLUSTER_ID) == null) {
            log.trace("{}: Binary input sensing cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.BINARY_INPUT_BASIC
                && attribute.getId() == ZclBinaryInputBasicCluster.ATTR_PRESENTVALUE) {
            Boolean value = (Boolean) val;
            if (value == Boolean.TRUE) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}

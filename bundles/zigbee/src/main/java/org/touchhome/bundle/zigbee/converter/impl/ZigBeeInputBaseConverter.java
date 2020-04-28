package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.util.concurrent.ExecutionException;

@Log4j2
public abstract class ZigBeeInputBaseConverter extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclCluster zclCluster;

    public abstract int getInputAttributeId();

    public abstract ZclClusterType getZclClusterType();

    @Override
    public boolean initializeDevice() {
        log.debug("{}/{}: Initialising {} device cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId(), getClass().getSimpleName());

        ZclCluster zclCluster = getZclCluster();
        if (zclCluster != null) {
            try {
                CommandResult bindResponse = bind(zclCluster).get();
                if (bindResponse.isSuccess()) {
                    // Configure reporting - no faster than once per second - no slower than 2 hours.

                    ZclAttribute attribute = zclCluster.getAttribute(this.getInputAttributeId());
                    CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 0.1).get();
                    handleReportingResponse(reportingResponse);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean initializeConverter() {
        zclCluster = getZclCluster();
        if (zclCluster != null) {
            zclCluster.addAttributeListener(this);
            return true;
        }
        return false;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing device input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        zclCluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        ZclAttribute attribute = zclCluster.getAttribute(this.getInputAttributeId());
        attribute.readValue(this.getInputAttributeId());
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(getZclClusterType().getId()) == null) {
            log.trace("{}/{}: Binary input sensing cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == getZclClusterType() && attribute.getId() == this.getInputAttributeId()) {
            updateValue(val);
        }
    }

    private void updateValue(Object val) {
        if (val instanceof Double) {
            updateChannelState(new DecimalType((Double) val));
        } else if (val instanceof Integer) {
            updateChannelState(new DecimalType((Integer) val));
        } else {
            throw new IllegalStateException("Unable to find value handler for type: " + val);
        }
    }

    private ZclCluster getZclCluster() {
        ZclCluster zclCluster = endpoint.getInputCluster(getZclClusterType().getId());
        if (zclCluster == null) {
            log.error("{}/{}: Error opening multistate binary input cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return null;
        }
        return zclCluster;
    }
}

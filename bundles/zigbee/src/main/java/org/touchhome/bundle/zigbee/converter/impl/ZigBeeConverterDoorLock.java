package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.ExecutionException;

/**
 * This channel supports changes through attribute updates to the door lock state. ON=Locked, OFF=Unlocked.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:door_state", clientClusters = {ZclDoorLockCluster.CLUSTER_ID})
public class ZigBeeConverterDoorLock extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclDoorLockCluster cluster;

    @Override
    public boolean initializeDevice() {
        ZclDoorLockCluster serverCluster = (ZclDoorLockCluster) endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device door lock controls", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = serverCluster.setDoorStateReporting(1, REPORTING_PERIOD_DEFAULT_MAX)
                        .get();
                handleReportingResponseHight(reportingResponse);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclDoorLockCluster) endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device door lock controls", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        // Add the listener
        cluster.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing device door lock cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        cluster.getDoorState(0);
    }

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        if (command == OnOffType.ON) {
            cluster.lockDoorCommand(new ByteArray(new byte[0]));
        } else {
            cluster.unlockDoorCommand(new ByteArray(new byte[0]));
        }
    }*/

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclDoorLockCluster.CLUSTER_ID) == null) {
            log.trace("{}/{}: Door lock cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.DOOR_LOCK
                && attribute.getId() == ZclDoorLockCluster.ATTR_LOCKSTATE) {
            Integer value = (Integer) val;
            if (value != null && value == 1) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }
}

package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.*;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneStatusChangeNotificationCommand;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.ExecutionException;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster.ATTR_ZONETYPE;

/**
 * Converter for the IAS zone sensors. This is an abstract class used as a base for different IAS sensors.
 */
@Log4j2
public abstract class ZigBeeConverterIas extends ZigBeeBaseChannelConverter
        implements ZclCommandListener, ZclAttributeListener {

    /**
     * CIE Zone Status Attribute flags
     */
    protected static final int CIE_ALARM1 = 0x0001;
    protected static final int CIE_ALARM2 = 0x0002;
    protected static final int CIE_TAMPER = 0x0004;
    protected static final int CIE_BATTERY = 0x0008;
    protected static final int CIE_SUPERVISION = 0x0010;
    protected static final int CIE_RESTORE = 0x0020;
    protected static final int CIE_TROUBLE = 0x0040;
    protected static final int CIE_ACMAINS = 0x0080;
    protected static final int CIE_TEST = 0x0100;
    protected static final int CIE_BATTERYDEFECT = 0x0200;
    protected int bitTest = CIE_ALARM1;
    private ZclIasZoneCluster clusterIasZone;

    @Override
    public boolean initializeDevice() {
        log.debug("{}: Initialising device IAS Zone cluster for {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                zigBeeConverterEndpoint.getClusterName());

        ZclIasZoneCluster serverClusterIasZone = (ZclIasZoneCluster) endpoint
                .getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (serverClusterIasZone == null) {
            log.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterIasZone).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                ZclAttribute attribute = serverClusterIasZone.getAttribute(ZclIasZoneCluster.ATTR_ZONESTATUS);
                CommandResult reportingResponse = serverClusterIasZone
                        .setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.debug("{}: Exception configuring ias zone status reporting", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        log.debug("{}: Initialising device IAS Zone cluster for {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                zigBeeConverterEndpoint.getClusterName());

        clusterIasZone = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        if (clusterIasZone == null) {
            log.error("{}: Error opening IAS zone cluster", endpoint.getIeeeAddress());
            return false;
        }
        // Add a listener, then request the status
        clusterIasZone.addCommandListener(this);
        clusterIasZone.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}: Closing device IAS zone cluster", endpoint.getIeeeAddress());

        clusterIasZone.removeCommandListener(this);
        clusterIasZone.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        clusterIasZone.getZoneStatus(0);
    }

    protected boolean supportsIasChannel(ZigBeeEndpoint endpoint, ZoneTypeEnum requiredZoneType) {
        if (!hasIasZoneInputCluster(endpoint)) {
            return false;
        }

        ZclIasZoneCluster cluster = (ZclIasZoneCluster) endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID);
        Integer zoneTypeId = null;
        ZclAttribute zclAttribute = cluster.getAttribute(ATTR_ZONETYPE);
        for (int retry = 0; retry < 3; retry++) {
            zoneTypeId = (Integer) zclAttribute.readValue(Long.MAX_VALUE);
            if (zoneTypeId != null) {
                break;
            }
        }
        if (zoneTypeId == null) {
            log.debug("{}: Did not get IAS zone type", endpoint.getIeeeAddress());
            return false;
        }
        ZoneTypeEnum zoneType = ZoneTypeEnum.getByValue(zoneTypeId);
        log.debug("{}: IAS zone type {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), zoneType);
        return zoneType == requiredZoneType;
    }

    protected boolean hasIasZoneInputCluster(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasZoneCluster.CLUSTER_ID) == null) {
            log.trace("{}: IAS zone cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        log.debug("{}: ZigBee command report {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), command);
        if (command instanceof ZoneStatusChangeNotificationCommand) {
            ZoneStatusChangeNotificationCommand zoneStatus = (ZoneStatusChangeNotificationCommand) command;
            updateChannelState(zoneStatus.getZoneStatus());

            clusterIasZone.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        return false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.IAS_ZONE
                && attribute.getId() == ZclIasZoneCluster.ATTR_ZONESTATUS) {
            updateChannelState((Integer) val);
        }
    }

    private void updateChannelState(Integer state) {
        updateChannelState(((state & bitTest) != 0) ? OnOffType.ON : OnOffType.OFF);
    }
}

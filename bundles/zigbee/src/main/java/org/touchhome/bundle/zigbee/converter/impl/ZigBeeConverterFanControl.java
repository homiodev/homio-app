package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclFanControlCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.DecimalType;

import java.util.concurrent.ExecutionException;

/**
 * This channel supports fan control
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:fancontrol", clientClusters = {ZclFanControlCluster.CLUSTER_ID})
public class ZigBeeConverterFanControl extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private static final int MODE_OFF = 0;
    private static final int MODE_LOW = 1;
    private static final int MODE_MEDIUM = 2;
    private static final int MODE_HIGH = 3;
    private static final int MODE_ON = 4;
    private static final int MODE_AUTO = 5;

    private ZclFanControlCluster cluster;
    private ZclAttribute fanModeAttribute;

    @Override
    public boolean initializeDevice() {
        ZclFanControlCluster serverCluster = (ZclFanControlCluster) endpoint
                .getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);
                CommandResult reportingResponse = attribute.setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponseHight(reportingResponse);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device fan controls", endpoint.getIeeeAddress());
            return false;
        }

        fanModeAttribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODE);

        // TODO: Detect the supported features and provide these as a description
        ZclAttribute fanSequenceAttribute = cluster.getAttribute(ZclFanControlCluster.ATTR_FANMODESEQUENCE);
        Integer sequence = (Integer) fanSequenceAttribute.readValue(Long.MAX_VALUE);
        /*if (sequence != null) {
            List<StateOption> options = new ArrayList<>();
            switch (sequence) {
                case 0:
                    options.addEnum(new StateOption("1", "Low"));
                    options.addEnum(new StateOption("2", "Medium"));
                    options.addEnum(new StateOption("3", "High"));
                case 1:
                    options.addEnum(new StateOption("1", "Low"));
                    options.addEnum(new StateOption("3", "High"));
                    break;
                case 2:
                    options.addEnum(new StateOption("1", "Low"));
                    options.addEnum(new StateOption("2", "Medium"));
                    options.addEnum(new StateOption("3", "High"));
                    options.addEnum(new StateOption("5", "Auto"));
                    break;
                case 3:
                    options.addEnum(new StateOption("1", "Low"));
                    options.addEnum(new StateOption("3", "High"));
                    options.addEnum(new StateOption("5", "Auto"));
                    break;
                case 4:
                    options.addEnum(new StateOption("4", "On"));
                    options.addEnum(new StateOption("5", "Auto"));
                    break;
                default:
                    log.error("{}/{}: Unknown fan mode sequence {}", endpoint.getIeeeAddress(),endpoint.getEndpointId(), sequence);
                    break;
            }

            stateDescription = new StateDescription(BigDecimal.ZERO, BigDecimal.valueOf(9), BigDecimal.valueOf(1), "",
                    true, options);
        }*/

        // Add the listener
        cluster.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing device fan control cluster", endpoint.getIeeeAddress());

        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        fanModeAttribute.readValue(0);
    }

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        int value;
        if (command instanceof OnOffType) {
            value = command == OnOffType.ON ? MODE_ON : MODE_OFF;
        } else if (command instanceof DecimalType) {
            value = ((DecimalType) command).intValue();
        } else {
            log.debug("{}/{}: Unabled to convert fan mode {}", endpoint.getIeeeAddress(),endpoint.getEndpointId(), command);
            return;
        }

        fanModeAttribute.writeValue(value);
    }*/

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclFanControlCluster cluster = (ZclFanControlCluster) endpoint.getInputCluster(ZclFanControlCluster.CLUSTER_ID);
        if (cluster == null) {
            log.trace("{}/{}: Fan control cluster not found", endpoint.getIeeeAddress());
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.FAN_CONTROL
                && attribute.getId() == ZclFanControlCluster.ATTR_FANMODE) {
            Integer value = (Integer) val;
            if (value != null) {
                updateChannelState(new DecimalType(value));
            }
        }
    }
}

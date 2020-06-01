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
 * Converter for the thermostat system mode channel. The SystemMode attribute specifies the current operating mode of
 * the thermostat,
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:thermostat_systemmode", clientClusters = {ZclThermostatCluster.CLUSTER_ID})
public class ZigBeeConverterThermostatSystemMode extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private final static int STATE_MIN = 0;
    private final static int STATE_MAX = 9;
    private final static int STATE_OFF = 0;
    private final static int STATE_AUTO = 1;

    private ZclThermostatCluster cluster;
    private ZclAttribute attribute;

    @Override
    public boolean initializeDevice() {
        ZclThermostatCluster serverCluster = (ZclThermostatCluster) endpoint
                .getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                ZclAttribute attribute = serverCluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
                CommandResult reportingResponse = attribute
                        .setReporting(1, REPORTING_PERIOD_DEFAULT_MAX).get();
                handleReportingResponse(reportingResponse);
            } else {
                log.debug("{}/{}: Failed to bind thermostat cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        cluster = (ZclThermostatCluster) endpoint.getInputCluster(ZclThermostatCluster.CLUSTER_ID);
        if (cluster == null) {
            log.error("{}/{}: Error opening device thermostat cluster", endpoint.getIeeeAddress());
            return false;
        }

        attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_SYSTEMMODE);
        if (attribute == null) {
            log.error("{}/{}: Error opening device thermostat system mode attribute", endpoint.getIeeeAddress());
            return false;
        }

        // Sequence of operation defines the allowable modes
        ZclAttribute attribute = cluster.getAttribute(ZclThermostatCluster.ATTR_CONTROLSEQUENCEOFOPERATION);
        Integer states = (Integer) attribute.readValue(Long.MAX_VALUE);
        /*List<StateOption> options = new ArrayList<>();
        options.add(new StateOption("0", "Off"));
        options.add(new StateOption("1", "Auto"));
        if (states != null && states != 0 && states != 1) {
            options.add(new StateOption("4", "Heat"));
            options.add(new StateOption("5", "Emergency Heating"));
        }
        if (states != null && states != 3 && states != 6) {
            options.add(new StateOption("3", "Cool"));
            options.add(new StateOption("6", "Precooling"));
        }
        options.add(new StateOption("7", "Fan Only"));
        options.add(new StateOption("8", "Dry"));
        options.add(new StateOption("9", "Sleep"));

        stateDescription = new StateDescription(BigDecimal.ZERO, BigDecimal.valueOf(9), BigDecimal.valueOf(1), "", true,
                options);*/

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
        Integer value = null;
        if (command instanceof OnOffType) {
            // OnOff switches between OFF=OFF and ON=AUTO
            value = ((OnOffType) command) == OnOffType.ON ? STATE_AUTO : STATE_OFF;
        } else if (command instanceof Number) {
            value = ((Number) command).intValue();
        }

        if (value == null) {
            log.warn("{}/{}: System mode command {} [{}] was not processed", endpoint.getIeeeAddress(), command,
                    command.getClass().getSimpleName());
            return;
        }

        if (value < STATE_MIN || value > STATE_MAX) {
            log.warn("{}/{}: System mode command {} [{}], value {}, was out of limits", endpoint.getIeeeAddress(),
                    command, command.getClass().getSimpleName(), value);
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
            log.trace("{}/{}: Thermostat cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        try {
            if (!cluster.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = cluster.getSystemMode(Long.MAX_VALUE);
                if (capabilities == null) {
                    log.trace("{}/{}: Thermostat system mode returned null", endpoint.getIeeeAddress());
                    return false;
                }
            } else if (!cluster.isAttributeSupported(ZclThermostatCluster.ATTR_SYSTEMMODE)) {
                log.trace("{}/{}: Thermostat system mode not supported", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}/{}: Exception discovering attributes in thermostat cluster", endpoint.getIeeeAddress(), e);
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.THERMOSTAT
                && attribute.getId() == ZclThermostatCluster.ATTR_SYSTEMMODE) {
            Integer value = (Integer) val;
            updateChannelState(new DecimalType(value));
        }
    }
}

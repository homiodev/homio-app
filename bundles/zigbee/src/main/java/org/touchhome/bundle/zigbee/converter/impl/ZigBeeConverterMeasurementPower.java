package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclElectricalMeasurementCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.QuantityType;
import tec.uom.se.unit.Units;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

@Log4j2
@ZigBeeConverter(name = "zigbee:electrical_activepower", clientClusters = {ZclElectricalMeasurementCluster.CLUSTER_ID})
public class ZigBeeConverterMeasurementPower extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private ZclElectricalMeasurementCluster clusterMeasurement;

    private Integer divisor;
    private Integer multiplier;

    @Override
    public boolean initializeDevice() {
        log.debug("{}/{}: Initialising electrical measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        ZclElectricalMeasurementCluster serverClusterMeasurement = (ZclElectricalMeasurementCluster) endpoint.getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (serverClusterMeasurement == null) {
            log.error("{}/{}: Error opening electrical measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterMeasurement).get();
            if (bindResponse.isSuccess()) {
                ZclAttribute attribute = serverClusterMeasurement.getAttribute(ZclElectricalMeasurementCluster.ATTR_ACTIVEPOWER);
                // Configure reporting - no faster than once per second - no slower than 2 hours.
                CommandResult reportingResponse = serverClusterMeasurement.setReporting(attribute, 3, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
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
        clusterMeasurement = (ZclElectricalMeasurementCluster) endpoint.getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (clusterMeasurement == null) {
            log.error("{}/{}: Error opening electrical measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        determineDivisorAndMultiplier(clusterMeasurement);

        // Add a listener, then request the status
        clusterMeasurement.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing electrical measurement cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        clusterMeasurement.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        clusterMeasurement.getActivePower(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclElectricalMeasurementCluster cluster = (ZclElectricalMeasurementCluster) endpoint.getInputCluster(ZclElectricalMeasurementCluster.CLUSTER_ID);
        if (cluster == null) {
            log.trace("{}/{}: Electrical measurement cluster not found", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            if (!cluster.discoverAttributes(false).get()
                    && !cluster.isAttributeSupported(ZclElectricalMeasurementCluster.ATTR_ACTIVEPOWER)) {
                log.trace("{}: Electrical measurement cluster active power not supported",
                        endpoint.getIeeeAddress());

                return false;
            } else if (cluster.getActivePower(Long.MAX_VALUE) == null) {
                log.trace("{}: Electrical measurement cluster active power returned null", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}: Exception discovering attributes in electrical measurement cluster", endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.ELECTRICAL_MEASUREMENT
                && attribute.getId() == ZclElectricalMeasurementCluster.ATTR_ACTIVEPOWER) {
            Integer value = (Integer) val;
            BigDecimal valueInWatt = BigDecimal.valueOf(value * multiplier / divisor);
            updateChannelState(new QuantityType<>(valueInWatt, Units.WATT));
        }
    }

    private void determineDivisorAndMultiplier(ZclElectricalMeasurementCluster serverClusterMeasurement) {
        divisor = serverClusterMeasurement.getAcPowerDivisor(Long.MAX_VALUE);
        multiplier = serverClusterMeasurement.getAcPowerMultiplier(Long.MAX_VALUE);
        if (divisor == null || multiplier == null) {
            divisor = 1;
            multiplier = 1;
        }
    }

}

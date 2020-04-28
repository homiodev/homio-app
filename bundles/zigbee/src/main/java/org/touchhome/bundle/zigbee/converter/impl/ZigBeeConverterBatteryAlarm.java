package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.StringType;

import java.util.concurrent.ExecutionException;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

/**
 * Converter for a battery alarm channel.
 * <p>
 * This converter relies on reports for the BatteryAlarmState attribute of the power configuration cluster, setting the
 * state of the battery alarm channel depending on the bits set in the BatteryAlarmState.
 * <p>
 * Possible future improvements:
 * <ul>
 * <li>The BatteryAlarmState provides battery level information for up to three batteries; this converter only considers
 * the information for the first battery.
 * <li>Devices might use alarms from the Alarms cluster instead of the BatteryAlarmState attribute to indicate battery
 * alarms. This is currently not supported by this converter.
 * <li>Devices might permit to configure the four battery level/voltage thresholds on which battery alarms are signaled;
 * such configuration is currently not supported.
 * </ul>
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:battery_alarm", clientClusters = {ZclPowerConfigurationCluster.CLUSTER_ID})
public class ZigBeeConverterBatteryAlarm extends ZigBeeBaseChannelConverter implements ZclAttributeListener {

    private static final String STATE_OPTION_BATTERY_MIN_THRESHOLD = "minThreshold";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_1 = "threshold1";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_2 = "threshold2";
    public static final String STATE_OPTION_BATTERY_THRESHOLD_3 = "threshold3";
    public static final String STATE_OPTION_BATTERY_NO_THRESHOLD = "noThreshold";

    private static final int ALARMSTATE_MIN_REPORTING_INTERVAL = (int) ofMinutes(10).getSeconds();
    private static final int ALARMSTATE_MAX_REPORTING_INTERVAL = (int) ofHours(2).getSeconds();

    private static final int MIN_THRESHOLD_BITMASK = 0b0001;
    private static final int THRESHOLD_1_BITMASK = 0b0010;
    private static final int THRESHOLD_2_BITMASK = 0b0100;
    private static final int THRESHOLD_3_BITMASK = 0b1000;

    private static final int BATTERY_ALARM_POLLING_PERIOD = (int) ofMinutes(30).getSeconds();

    private ZclPowerConfigurationCluster cluster;

    @Override
    public boolean initializeDevice() {
        log.debug("{}/{}: Initialising device battery alarm converter", endpoint.getIeeeAddress(), endpoint.getEndpointId());

        ZclPowerConfigurationCluster serverCluster = (ZclPowerConfigurationCluster) endpoint
                .getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (serverCluster == null) {
            log.error("{}/{}: Error opening power configuration cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverCluster).get();
            if (bindResponse.isSuccess()) {
                CommandResult reportingResponse = serverCluster.setReporting(ATTR_BATTERYALARMSTATE, ALARMSTATE_MIN_REPORTING_INTERVAL, ALARMSTATE_MAX_REPORTING_INTERVAL).get();
                handleReportingResponse(reportingResponse, BATTERY_ALARM_POLLING_PERIOD, ALARMSTATE_MAX_REPORTING_INTERVAL);
            } else {
                pollingPeriod = BATTERY_ALARM_POLLING_PERIOD;
                log.debug("Could not bind to the power configuration cluster; polling battery alarm state every {} seconds", BATTERY_ALARM_POLLING_PERIOD);
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}/{}: Exception setting reporting of battery alarm state ", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
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

        // Add a listener
        cluster.addAttributeListener(this);
        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}/{}: Closing battery alarm converter", endpoint.getIeeeAddress(), endpoint.getEndpointId());
        cluster.removeAttributeListener(this);
    }

    @Override
    protected void handleRefresh() {
        cluster.getBatteryAlarmState(0);
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclPowerConfigurationCluster powerConfigurationCluster = (ZclPowerConfigurationCluster) endpoint.getInputCluster(ZclPowerConfigurationCluster.CLUSTER_ID);
        if (powerConfigurationCluster == null) {
            log.trace("{}: Power configuration cluster not found on endpoint {}", endpoint.getIeeeAddress(), endpoint.getEndpointId());
            return false;
        }

        try {
            if (!powerConfigurationCluster.discoverAttributes(false).get() && !powerConfigurationCluster.isAttributeSupported(ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE)) {
                log.trace("{}: Power configuration cluster battery alarm state not supported", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}/{}: Exception discovering attributes in power configuration cluster", endpoint.getIeeeAddress(), endpoint.getEndpointId(), e);
            return false;
        }
        return true;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        if (attribute.getCluster() == ZclClusterType.POWER_CONFIGURATION && attribute.getId() == ZclPowerConfigurationCluster.ATTR_BATTERYALARMSTATE) {

            log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);

            // The value is a 32-bit bitmap, represented by an Integer
            Integer value = (Integer) val;

            if ((value & MIN_THRESHOLD_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_MIN_THRESHOLD));
            } else if ((value & THRESHOLD_1_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_1));
            } else if ((value & THRESHOLD_2_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_2));
            } else if ((value & THRESHOLD_3_BITMASK) != 0) {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_THRESHOLD_3));
            } else {
                updateChannelState(new StringType(STATE_OPTION_BATTERY_NO_THRESHOLD));
            }
        }
    }
}

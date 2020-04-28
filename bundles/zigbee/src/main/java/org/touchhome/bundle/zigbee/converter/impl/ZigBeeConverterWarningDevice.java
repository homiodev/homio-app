package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasWdCluster;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.warningdevice.SquawkType;
import org.touchhome.bundle.zigbee.converter.warningdevice.WarningType;

/**
 * Channel converter for warning devices, based on the IAS WD cluster.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:warning_device", clientClusters = {ZclIasWdCluster.CLUSTER_ID})
public class ZigBeeConverterWarningDevice extends ZigBeeBaseChannelConverter {

    private static final String CONFIG_PREFIX = "zigbee_iaswd_";
    private static final String CONFIG_MAXDURATION = CONFIG_PREFIX + "maxDuration";

    private ZclIasWdCluster iasWdCluster;

    @Override
    public boolean initializeDevice() {
        return true;
    }

    @Override
    public boolean initializeConverter() {
        iasWdCluster = (ZclIasWdCluster) endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID);
        if (iasWdCluster == null) {
            log.error("{}: Error opening warning device controls", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclIasWdCluster.CLUSTER_ID) == null) {
            log.trace("{}: IAS WD cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    /*@Override
    public void updateConfiguration( Configuration currentConfiguration,
            Map<String, Object> updatedParameters) {
        for (Entry<String, Object> updatedParameter : updatedParameters.entrySet()) {
            if (updatedParameter.getKey().startsWith(CONFIG_PREFIX)) {
                if (Objects.equals(updatedParameter.getValue(), currentConfiguration.get(updatedParameter.getKey()))) {
                    log.debug("Configuration update: Ignored {} as no change", updatedParameter.getKey());
                } else {
                    updateConfigParameter(currentConfiguration, updatedParameter);
                }
            }
        }
    }*/

    /*private void updateConfigParameter(Configuration currentConfiguration, Entry<String, Object> updatedParameter) {
        log.debug("{}: Update IAS WD configuration property {}->{} ({})", iasWdCluster.getZigBeeAddress(),
                updatedParameter.getKey(), updatedParameter.getValue(),
                updatedParameter.getValue().getClass().getSimpleName());

        if (CONFIG_MAXDURATION.equals(updatedParameter.getKey())) {
            iasWdCluster.setMaxDuration(((BigDecimal) (updatedParameter.getValue())).intValue());
            Integer response = iasWdCluster.getMaxDuration(0);

            if (response != null) {
                currentConfiguration.put(updatedParameter.getKey(), BigInteger.valueOf(response));
            }
        } else {
            log.warn("{}: Unhandled configuration property {}", iasWdCluster.getZigBeeAddress(),
                    updatedParameter.getKey());
        }
    }*/

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        if (iasWdCluster == null) {
            log.warn("{}: Warning device converter is not linked to a server and cannot accept commands",
                    endpoint.getIeeeAddress());
            return;
        }

        if (!(command instanceof StringType)) {
            log.warn("{}: This converter only supports string-type commands", endpoint.getIeeeAddress());
            return;
        }

        String commandString = ((StringType) command).toFullString();

        WarningType warningType = WarningType.parse(commandString);
        if (warningType != null) {
            sendWarning(warningType);
        } else {
            SquawkType squawkType = SquawkType.parse(commandString);
            if (squawkType != null) {
                squawk(squawkType);
            } else {
                log.warn("{}: Ignoring command that is neither warning nor squawk command: {}",
                        endpoint.getIeeeAddress(), commandString);
            }
        }
    }*/

    private void sendWarning(WarningType warningType) {
        iasWdCluster.startWarningCommand(
                makeWarningHeader(warningType.getWarningMode(), warningType.isUseStrobe(), warningType.getSirenLevel()),
                (int) warningType.getDuration().getSeconds());
    }

    private int makeWarningHeader(int warningMode, boolean useStrobe, int sirenLevel) {
        int result = 0;
        result |= warningMode;
        result |= (useStrobe ? 1 : 0) << 4;
        result |= sirenLevel << 6;
        return result;
    }

    private void squawk(SquawkType squawkType) {
        iasWdCluster.squawk(
                makeSquawkHeader(squawkType.getSquawkMode(), squawkType.isUseStrobe(), squawkType.getSquawkLevel()));
    }

    private Integer makeSquawkHeader(int squawkMode, boolean useStrobe, int squawkLevel) {
        int result = 0;
        result |= squawkMode;
        result |= (useStrobe ? 1 : 0) << 4;
        result |= squawkLevel << 6;
        return result;
    }

}

package org.touchhome.bundle.zigbee.converter.impl.config;

import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;

/**
 * Configuration handler for the
 */
@Log4j2
public class ZclOnOffSwitchConfig implements ZclClusterConfigHandler {

    private static final String CONFIG_ID = "zigbee_onoff_";
    private static final String CONFIG_OFFWAITTIME = CONFIG_ID + "offwaittime";
    private static final String CONFIG_ONTIME = CONFIG_ID + "ontime";
    private static final String CONFIG_STARTUPONOFF = CONFIG_ID + "startuponoff";

    private ZclOnOffCluster onoffCluster;

    @Override
    public boolean initialize(ZclCluster cluster) {
        onoffCluster = (ZclOnOffCluster) cluster;
        ZclLevelControlConfig.initCluster(onoffCluster.discoverAttributes(false), log, onoffCluster.getZigBeeAddress(), onoffCluster.getClusterName());

        // Build a list of configuration supported by this channel based on the attributes the cluster supports

        /*List<ParameterOption> options = new ArrayList<>();

        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_OFFWAITTIME)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_OFFWAITTIME, Type.INTEGER)
                    .withLabel("Off Wait Time")
                    .withDescription("Time in 100ms steps to ignore ON commands after an OFF command").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_ONTIME)) {
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_ONTIME, Type.INTEGER)
                    .withLabel("Auto OFF Time")
                    .withDescription("Time in 100ms steps to automatically turn off when sent with timed command")
                    .withDefault("65535").withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).build());
        }
        if (onoffCluster.isAttributeSupported(ZclOnOffCluster.ATTR_STARTUPONOFF)) {
            options = new ArrayList<ParameterOption>();
            options.add(new ParameterOption("0", "OFF"));
            options.add(new ParameterOption("1", "ON"));
            parameters.add(ConfigDescriptionParameterBuilder.create(CONFIG_STARTUPONOFF, Type.INTEGER)
                    .withLabel("Power on state").withDescription("The state to set after powering on").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(1)).withOptions(options)
                    .withLimitToOptions(true).build());
        }

        return !parameters.isEmpty();*/

        return true;
    }

    @Override
    public boolean updateConfiguration() {
        return false;
    }

    /*@Override
    public boolean updateConfiguration( Configuration currentConfiguration,
            Map<String, Object> configurationParameters) {

        boolean updated = false;
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            if (!configurationParameter.getKey().startsWith(CONFIG_ID)) {
                continue;
            }
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(),
                    currentConfiguration.get(configurationParameter.getKey()))) {
                log.debug("Configuration update: Ignored {} as no change", configurationParameter.getKey());
                continue;
            }

            log.debug("{}: Update LevelControl configuration property {}->{} ({})", onoffCluster.getZigBeeAddress(),
                    configurationParameter.getKey(), configurationParameter.getValue(),
                    configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_OFFWAITTIME:
                    response = configureAttribute(ZclOnOffCluster.ATTR_OFFWAITTIME, configurationParameter.getValue());
                    break;
                case CONFIG_ONTIME:
                    response = configureAttribute(ZclOnOffCluster.ATTR_ONTIME, configurationParameter.getValue());
                    break;
                case CONFIG_STARTUPONOFF:
                    response = configureAttribute(ZclOnOffCluster.ATTR_STARTUPONOFF, configurationParameter.getValue());
                    break;
                default:
                    log.warn("{}: Unhandled configuration property {}", onoffCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }

            if (response != null) {
                currentConfiguration.put(configurationParameter.getKey(), BigInteger.valueOf(response));
                updated = true;
            }
        }

        return updated;
    }*/

    private Integer configureAttribute(int attributeId, Object value) {
        ZclAttribute attribute = onoffCluster.getAttribute(attributeId);
        attribute.writeValue(((BigDecimal) (value)).intValue());
        return (Integer) attribute.readValue(0);
    }
}

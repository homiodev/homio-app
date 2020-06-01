package org.touchhome.bundle.zigbee.converter.impl.config;

import com.zsmartsystems.zigbee.ZigBeeEndpointAddress;
import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Configuration handler for the {@link ZclLevelControlCluster}
 */
@Log4j2
public class ZclLevelControlConfig implements ZclClusterConfigHandler {

    private static final String CONFIG_ID = "zigbee_levelcontrol_";
    private static final String CONFIG_DEFAULTTRANSITIONTIME = CONFIG_ID + "transitiontimedefault";
    private static final String CONFIG_ONOFFTRANSITIONTIME = CONFIG_ID + "transitiontimeonoff";
    private static final String CONFIG_ONTRANSITIONTIME = CONFIG_ID + "transitiontimeon";
    private static final String CONFIG_OFFTRANSITIONTIME = CONFIG_ID + "transitiontimeoff";
    private static final String CONFIG_ONLEVEL = CONFIG_ID + "onlevel";
    private static final String CONFIG_DEFAULTMOVERATE = CONFIG_ID + "defaultrate";

    private ZclLevelControlCluster levelControlCluster;
    private int defaultTransitionTime = 10;

    static void initCluster(Future<Boolean> booleanFuture, Logger log, ZigBeeEndpointAddress zigBeeAddress, String clusterName) {
        try {
            Boolean result = booleanFuture.get();
            if (!result) {
                log.debug("{}: Unable to get supported attributes for {}.", zigBeeAddress,
                        clusterName);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("{}: Error getting supported attributes for {}. ", zigBeeAddress,
                    clusterName, e);
        }
    }

    @Override
    public boolean initialize(ZclCluster cluster) {
        levelControlCluster = (ZclLevelControlCluster) cluster;
        initCluster(levelControlCluster.discoverAttributes(false), log, levelControlCluster.getZigBeeAddress(), levelControlCluster.getClusterName());

        /*List<ParameterOption> options = new ArrayList<>();
        options.addEnum(new ParameterOption("65535", "Use On/Off times"));
        parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTTRANSITIONTIME, Type.INTEGER)
                .withLabel("Default Transition Time")
                .withDescription("Default time in 100ms intervals to transition between ON and OFF").withDefault("0")
                .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                .withLimitToOptions(false).build());

        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONOFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_ONOFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On/Off Transition Time")
                    .withDescription("Time in 100ms intervals to transition between ON and OFF").withDefault("0")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.addEnum(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_ONTRANSITIONTIME, Type.INTEGER)
                    .withLabel("On Transition Time")
                    .withDescription("Time in 100ms intervals to transition from OFF to ON").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_OFFTRANSITIONTIME)) {
            options = new ArrayList<ParameterOption>();
            options.addEnum(new ParameterOption("65535", "Use On/Off transition time"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_OFFTRANSITIONTIME, Type.INTEGER)
                    .withLabel("Off Transition Time")
                    .withDescription("Time in 100ms intervals to transition from ON to OFF").withDefault("65535")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_ONLEVEL)) {
            options = new ArrayList<ParameterOption>();
            options.addEnum(new ParameterOption("255", "Not Set"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_ONLEVEL, Type.INTEGER).withLabel("On Level")
                    .withDescription("Default On level").withDefault("255").withMinimum(new BigDecimal(0))
                    .withMaximum(new BigDecimal(60000)).withOptions(options).withLimitToOptions(false).build());
        }
        if (levelControlCluster.isAttributeSupported(ZclLevelControlCluster.ATTR_DEFAULTMOVERATE)) {
            options = new ArrayList<ParameterOption>();
            options.addEnum(new ParameterOption("255", "Not Set"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_DEFAULTMOVERATE, Type.INTEGER)
                    .withLabel("Default move rate").withDescription("Move rate in steps per second").withDefault("255")
                    .withMinimum(new BigDecimal(0)).withMaximum(new BigDecimal(60000)).withOptions(options)
                    .withLimitToOptions(false).build());
        }

        return !parameters.isEmpty();*/

        return true;
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

            log.debug("{}: Update LevelControl configuration property {}->{} ({})",
                    levelControlCluster.getZigBeeAddress(), configurationParameter.getKey(),
                    configurationParameter.getValue(), configurationParameter.getValue().getClass().getSimpleName());
            Integer response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_ONOFFTRANSITIONTIME:
                    levelControlCluster
                            .setOnOffTransitionTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnOffTransitionTime(0);
                    break;
                case CONFIG_ONTRANSITIONTIME:
                    levelControlCluster
                            .setOnTransitionTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnTransitionTime(0);
                    break;
                case CONFIG_OFFTRANSITIONTIME:
                    levelControlCluster
                            .setOffTransitionTime((((BigDecimal) (configurationParameter.getValue())).intValue()));
                    response = levelControlCluster.getOffTransitionTime(0);
                    break;
                case CONFIG_ONLEVEL:
                    levelControlCluster.setOnLevel(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getOnLevel(0);
                    break;
                case CONFIG_DEFAULTMOVERATE:
                    levelControlCluster
                            .setDefaultMoveRate(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = levelControlCluster.getDefaultMoveRate(0);
                    break;
                case CONFIG_DEFAULTTRANSITIONTIME:
                    defaultTransitionTime = ((BigDecimal) (configurationParameter.getValue())).intValue();
                    break;
                default:
                    log.warn("{}: Unhandled configuration property {}", levelControlCluster.getZigBeeAddress(),
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

    @Override
    public boolean updateConfiguration() {
        return false;
    }

    /**
     * Gets the default transition time to be used when sending commands to the cluster
     *
     * @return the current defaultTransitionTime
     */
    public int getDefaultTransitionTime() {
        return defaultTransitionTime;
    }
}

package org.touchhome.bundle.zigbee.converter.impl.config;

import com.zsmartsystems.zigbee.zcl.ZclCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclDoorLockCluster;
import lombok.extern.log4j.Log4j2;

/**
 * Configuration handler for the {@link ZclDoorLockCluster}
 */
@Log4j2
public class ZclDoorLockConfig implements ZclClusterConfigHandler {

    private static final String CONFIG_ID = "zigbee_doorlock_";
    private static final String CONFIG_SOUNDVOLUME = CONFIG_ID + "soundvolume";
    private static final String CONFIG_ENABLEONETOUCHLOCKING = CONFIG_ID + "onetouchlocking";
    private static final String CONFIG_ENABLELOCALPROGRAMMING = CONFIG_ID + "localprogramming";
    private static final String CONFIG_AUTORELOCKTIME = CONFIG_ID + "autorelocktime";

    private ZclDoorLockCluster doorLockCluster;

    @Override
    public boolean initialize(ZclCluster cluster) {
        doorLockCluster = (ZclDoorLockCluster) cluster;
        ZclLevelControlConfig.initCluster(doorLockCluster.discoverAttributes(false), log, doorLockCluster.getZigBeeAddress(), doorLockCluster.getClusterName());

        // Build a list of configuration supported by this channel based on the attributes the cluster supports
        /*List<ParameterOption> options = new ArrayList<>();

        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_SOUNDVOLUME)) {
            options = new ArrayList<>();
            options.addEnum(new ParameterOption("0", "Silent"));
            options.addEnum(new ParameterOption("1", "Low"));
            options.addEnum(new ParameterOption("2", "High"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_SOUNDVOLUME, Type.INTEGER)
                    .withLabel("The sound volume of the door lock").withDescription("").withDefault("0")
                    .withOptions(options).build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_AUTORELOCKTIME)) {
            options = new ArrayList<>();
            options.addEnum(new ParameterOption("0", "Disabled"));
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_AUTORELOCKTIME, Type.INTEGER)
                    .withLabel("Enable one touch locking").withDescription("").withMinimum(BigDecimal.ZERO)
                    .withMaximum(new BigDecimal(3600)).withDefault("0").withOptions(options).build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLEONETOUCHLOCKING)) {
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_ENABLEONETOUCHLOCKING, Type.BOOLEAN)
                    .withLabel("Set auto relock time").withDescription("").withDefault("false").build());
        }
        if (doorLockCluster.isAttributeSupported(ZclDoorLockCluster.ATTR_ENABLELOCALPROGRAMMING)) {
            parameters.addEnum(ConfigDescriptionParameterBuilder.create(CONFIG_ENABLELOCALPROGRAMMING, Type.BOOLEAN)
                    .withLabel("Enable local programming").withDescription("").withDefault("false").build());
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
            Map<String, Object> updatedParameters) {
        boolean updated = false;
        for (Entry<String, Object> configurationParameter : updatedParameters.entrySet()) {
            if (!configurationParameter.getKey().startsWith(CONFIG_ID)) {
                continue;
            }

            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(),
                    currentConfiguration.get(configurationParameter.getKey()))) {
                log.debug("Configuration update: Ignored {} as no change", configurationParameter.getKey());
                continue;
            }

            log.debug("{}: Update DoorLock configuration property {}->{} ({})", doorLockCluster.getZigBeeAddress(),
                    configurationParameter.getKey(), configurationParameter.getValue(),
                    configurationParameter.getValue().getClass().getSimpleName());
            Object response = null;
            switch (configurationParameter.getKey()) {
                case CONFIG_ENABLEONETOUCHLOCKING:
                    doorLockCluster
                            .setEnableOneTouchLocking(((Boolean) (configurationParameter.getValue())).booleanValue());
                    response = Boolean.valueOf(doorLockCluster.getEnableOneTouchLocking(0));
                    break;
                case CONFIG_ENABLELOCALPROGRAMMING:
                    doorLockCluster
                            .setEnableLocalProgramming(((Boolean) configurationParameter.getValue()).booleanValue());
                    response = Boolean.valueOf(doorLockCluster.getEnableLocalProgramming(0));
                    break;
                case CONFIG_SOUNDVOLUME:
                    doorLockCluster.setSoundVolume(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = BigInteger.valueOf(doorLockCluster.getSoundVolume(0));
                    break;
                case CONFIG_AUTORELOCKTIME:
                    doorLockCluster.setAutoRelockTime(((BigDecimal) (configurationParameter.getValue())).intValue());
                    response = BigInteger.valueOf(doorLockCluster.getAutoRelockTime(0));
                    break;
                default:
                    log.warn("{}: Unhandled configuration property {}", doorLockCluster.getZigBeeAddress(),
                            configurationParameter.getKey());
                    break;
            }

            if (response != null) {
                currentConfiguration.put(configurationParameter.getKey(), response);
                updated = true;
            }
        }

        return updated;
    }*/
}

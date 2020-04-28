package org.touchhome.bundle.zigbee.converter.impl.config;

import com.zsmartsystems.zigbee.zcl.ZclCluster;

/**
 * Base configuration handler for the {@link ZclCluster}.
 * <p>
 * The configuration handler provides configuration services for a cluster. The handler supports the discovery of
 * configuration attributes within the remote device, and the generation of the ESH configuration descriptions. It is
 * then able to process configuration updates, and sends the attribute updates to the device. It provides a getter for
 * any local configuration parameters (ie those that are not attributes in the remote device).
 */
public interface ZclClusterConfigHandler {
    /**
     * Creates the list of {@link }. This method shall check the available attributes on the
     * remote device and create configuration parameters for each supported attribute that needs to be configurable.
     *
     * @param cluster the {@link ZclCluster} to get the configuration
     * @return true if this cluster has configuration descriptions
     */
    boolean initialize(ZclCluster cluster);

    boolean updateConfiguration();
}

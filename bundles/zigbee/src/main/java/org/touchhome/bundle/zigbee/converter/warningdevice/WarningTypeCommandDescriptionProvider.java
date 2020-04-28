package org.touchhome.bundle.zigbee.converter.warningdevice;

import java.util.List;

/**
 * Interface for providers of additional warning device configurations that are provided as command options for warning
 * device zigbeeRequireEndpoints.
 */
public interface WarningTypeCommandDescriptionProvider {

    /**
     * @return A map mapping labels for warning/squawk command descriptions (to be used by UIs) to the serializes
     * warning/squawk commands.
     */
    List<String> getWarningAndSquawkCommandOptions();

}

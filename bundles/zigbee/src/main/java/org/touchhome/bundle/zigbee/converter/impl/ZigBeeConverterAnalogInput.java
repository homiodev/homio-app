package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.zcl.clusters.ZclAnalogInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ZigBeeConverter(name = "zigbee:analoginput", clientClusters = {ZclAnalogInputBasicCluster.CLUSTER_ID})
public class ZigBeeConverterAnalogInput extends ZigBeeInputBaseConverter {

    @Override
    public int getInputAttributeId() {
        return ZclAnalogInputBasicCluster.ATTR_PRESENTVALUE;
    }

    @Override
    public ZclClusterType getZclClusterType() {
        return ZclClusterType.ANALOG_INPUT_BASIC;
    }
}

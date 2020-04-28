package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.zcl.clusters.ZclMultistateInputBasicCluster;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ZigBeeConverter(name = "zigbee:multistateinput", clientClusters = {ZclMultistateInputBasicCluster.CLUSTER_ID})
public class ZigBeeConverterMultistateInput extends ZigBeeInputBaseConverter {

    @Override
    public int getInputAttributeId() {
        return ZclMultistateInputBasicCluster.ATTR_PRESENTVALUE;
    }

    @Override
    public ZclClusterType getZclClusterType() {
        return ZclClusterType.MULTISTATE_INPUT_BASIC;
    }
}

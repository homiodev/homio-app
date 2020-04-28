package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;

/**
 * Converter for the IAS presence sensor.
 */
@ZigBeeConverter(name = "zigbee:ias_motionpresence", clientClusters = {ZclIasZoneCluster.CLUSTER_ID})
public class ZigBeeConverterIasMotionPresence extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_ALARM2;
        return super.initializeConverter();
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        return supportsIasChannel(endpoint, ZoneTypeEnum.MOTION_SENSOR);
    }
}

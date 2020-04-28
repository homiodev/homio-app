package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;

/**
 * Converter for the IAS Standard CIE System sensor.
 */
@ZigBeeConverter(name = "zigbee:ias_standard_system", clientClusters = {ZclIasZoneCluster.CLUSTER_ID})
public class ZigBeeConverterIasCieSystem extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_ALARM1;
        return super.initializeConverter();
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        return supportsIasChannel(endpoint, ZoneTypeEnum.STANDARD_CIE);
    }
}

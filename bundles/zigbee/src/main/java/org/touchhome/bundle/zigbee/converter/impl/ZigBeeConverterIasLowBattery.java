package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * Converter for the IAS low battery indicator.
 */
@ZigBeeConverter(name = "zigbee:iaslowbattery", description = "Low battery alarm", clientClusters = {ZclIasZoneCluster.CLUSTER_ID})
public class ZigBeeConverterIasLowBattery extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_BATTERY;
        return super.initializeConverter();
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        return hasIasZoneInputCluster(endpoint);
    }
}

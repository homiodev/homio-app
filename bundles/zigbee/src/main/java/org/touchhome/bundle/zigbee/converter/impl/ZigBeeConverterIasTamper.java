package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;

/**
 * Converter for the IAS tamper.
 */
@ZigBeeConverter(name = "zigbee:ias_tamper", clientClusters = {ZclIasZoneCluster.CLUSTER_ID})
public class ZigBeeConverterIasTamper extends ZigBeeConverterIas {

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_TAMPER;
        return super.initializeConverter();
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        return hasIasZoneInputCluster(endpoint);
    }

}

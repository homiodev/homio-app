package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import com.zsmartsystems.zigbee.zcl.clusters.iaszone.ZoneTypeEnum;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;

/**
 * Converter for the IAS water sensor.
 */
@ZigBeeConverter(name = ZigBeeConverterIasWaterSensor.CLUSTER_NAME, description = "Liquid alarm", clientClusters = {ZclIasZoneCluster.CLUSTER_ID}, linkType = DeviceChannelLinkType.Boolean)
public class ZigBeeConverterIasWaterSensor extends ZigBeeConverterIas {
    public static final String CLUSTER_NAME = "zigbee:ias_water";

    @Override
    public boolean initializeConverter() {
        bitTest = CIE_ALARM1;
        return super.initializeConverter();
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        return supportsIasChannel(endpoint, ZoneTypeEnum.WATER_SENSOR);
    }
}

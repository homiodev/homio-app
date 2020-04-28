package org.touchhome.bundle.zigbee.requireEndpoint;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.touchhome.bundle.api.util.SmartUtils;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;

@Getter
@Setter
@ToString
public class RequireEndpoint {
    private int endpoint;
    private int inputCluster;
    private String typeId;

    public boolean match(ZigBeeConverter zigBeeConverter) {
        return zigBeeConverter.name().equals(typeId) && SmartUtils.containsAny(zigBeeConverter.clientClusters(), inputCluster);
    }

    public boolean match(ZigBeeConverterEndpoint converter) {
        return converter.getClusterName().equals(typeId) && converter.getEndpointId() == endpoint &&
                converter.getClusterId() == inputCluster;
    }
}

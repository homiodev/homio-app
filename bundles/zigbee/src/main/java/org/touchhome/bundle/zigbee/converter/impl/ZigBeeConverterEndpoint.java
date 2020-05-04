package org.touchhome.bundle.zigbee.converter.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Getter
@ToString
@RequiredArgsConstructor
public class ZigBeeConverterEndpoint {
    private final String ieeeAddress;
    private final int clusterId;
    private final int endpointId;
    private final String clusterName;

    private ZigBeeConverter zigBeeConverter;

    ZigBeeConverterEndpoint(ZigBeeConverter zigBeeConverter, String ieeeAddress, int endpointId) {
        this(ieeeAddress, zigBeeConverter.clientClusters()[0], endpointId, zigBeeConverter.name());
        this.zigBeeConverter = zigBeeConverter;
    }

    public ZigBeeDeviceStateUUID toUUID() {
        return new ZigBeeDeviceStateUUID(ieeeAddress, clusterId, endpointId, clusterName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ZigBeeConverterEndpoint that = (ZigBeeConverterEndpoint) o;
        return clusterId == that.clusterId &&
                endpointId == that.endpointId &&
                Objects.equals(ieeeAddress, that.ieeeAddress) &&
                Objects.equals(clusterName, that.clusterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ieeeAddress, clusterId, endpointId, clusterName);
    }

    public String getClusterDescription() {
        return defaultIfEmpty(zigBeeConverter.description(), clusterName);
    }
}

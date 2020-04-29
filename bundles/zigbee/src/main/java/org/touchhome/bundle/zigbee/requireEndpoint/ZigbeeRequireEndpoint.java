package org.touchhome.bundle.zigbee.requireEndpoint;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.zigbee.ZigBeeNodeDescription;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class ZigbeeRequireEndpoint {
    private String modelId;
    private String name;
    private String image;
    private boolean disablePooling = false;
    private List<RequireEndpoint> requireEndpoints;

    boolean matchAllTypes(Collection<ZigBeeNodeDescription.ChannelDescription> channels) {
        if (requireEndpoints == null) {
            return false;
        }
        for (RequireEndpoint requireEndpoint : requireEndpoints) {
            if (!matchType(requireEndpoint, channels)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchType(RequireEndpoint requireEndpoint, Collection<ZigBeeNodeDescription.ChannelDescription> channels) {
        for (ZigBeeNodeDescription.ChannelDescription channel : channels) {
            if (requireEndpoint.getEndpoint() == channel.getChannelUUID().getEndpointId() &&
                    requireEndpoint.getInputCluster() == channel.getChannelUUID().getClusterId() &&
                    requireEndpoint. getTypeId().equals(channel.getChannelUUID().getClusterName())) {
                return true;
            }
        }
        return false;
    }
}

package org.touchhome.bundle.zigbee.requireEndpoint;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.util.SmartUtils;
import org.touchhome.bundle.zigbee.ZigBeeNodeDescription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class ZigbeeRequireEndpoints {

    private static final ZigbeeRequireEndpoints INSTANCE;

    static {
        INSTANCE = new ZigbeeRequireEndpoints();
        for (ZigbeeRequireEndpoints file : SmartUtils.readJSON("zigbee/device-properties.json", ZigbeeRequireEndpoints.class)) {
            INSTANCE.getZigbeeRequireEndpoints().addAll(file.getZigbeeRequireEndpoints());
        }
    }

    public static ZigbeeRequireEndpoints get() {
        return INSTANCE;
    }

    public String getImage(String modelId) {
        return zigbeeRequireEndpoints.stream().filter(c -> c.getModelId().equals(modelId)).map(ZigbeeRequireEndpoint::getImage).findAny().orElse(null);
    }

    @Getter
    @Setter
    private List<ZigbeeRequireEndpoint> zigbeeRequireEndpoints = new ArrayList<>();

    public ZigbeeRequireEndpoint findByNode(ZigBeeNodeDescription zigBeeNodeDescription) {
        return this.zigbeeRequireEndpoints.stream().filter(c -> c.matchAllTypes(zigBeeNodeDescription.getChannels())).findAny().orElse(null);
    }

    public Stream<RequireEndpoint> getRequireEndpoints(String modelIdentifier) {
        return zigbeeRequireEndpoints.stream().filter(c->c.getModelId().equals(modelIdentifier))
                .map(ZigbeeRequireEndpoint::getRequireEndpoints).filter(Objects::nonNull).flatMap(Collection::stream);
    }

    public ZigbeeRequireEndpoint getZigbeeRequireEndpoint(String modelIdentifier) {
        return zigbeeRequireEndpoints.stream().filter(c->c.getModelId().equals(modelIdentifier)).findAny().orElse(null);
    }
}

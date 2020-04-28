package org.touchhome.bundle.zigbee.converter.impl;

import org.touchhome.bundle.api.link.DeviceChannelLinkType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZigBeeConverter {
    /**
     * Gets the cluster IDs that are implemented within the converter on the client side.
     *
     * @return Set of cluster IDs supported by the converter
     */
    int[] clientClusters() default 0;

    /**
     * Gets the cluster IDs that are implemented within the converter on the server side.
     *
     * @return Set of cluster IDs supported by the converter
     */
    int[] serverClusters() default 0;

    String name();

    String description() default "";

    DeviceChannelLinkType linkType() default DeviceChannelLinkType.None;
}

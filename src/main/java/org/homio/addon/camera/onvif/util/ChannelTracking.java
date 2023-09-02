package org.homio.addon.camera.onvif.util;

import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

/**
 * Can be used to find the handle for an HTTP channel if you know the URL. The reply can optionally be stored for later use.
 */
@Getter
public class ChannelTracking {

    private final String requestUrl;
    private final Channel channel;
    private @Setter String reply = "";

    public ChannelTracking(Channel channel, String requestUrl) {
        this.channel = channel;
        this.requestUrl = requestUrl;
    }
}

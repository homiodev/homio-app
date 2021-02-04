package org.touchhome.app.videoStream.onvif.util;

import io.netty.channel.Channel;

/**
 * Can be used to find the handle for a HTTP channel if you know the URL. The reply can optionally be stored for later use.
 */
public class ChannelTracking {
    private String storedReply = "";
    private String requestUrl = "";
    private Channel channel;

    public ChannelTracking(Channel channel, String requestUrl) {
        this.channel = channel;
        this.requestUrl = requestUrl;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getReply() {
        return storedReply;
    }

    public void setReply(String replyToStore) {
        storedReply = replyToStore;
    }

    public void setChannel(Channel ch) {
        channel = ch;
    }
}

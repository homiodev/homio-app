package org.touchhome.app.videoStream.onvif;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;

/**
 * used by Netty to decode Onvif traffic into message Strings.
 */
@Log4j2
public class OnvifCodec extends ChannelDuplexHandler {

    private String incomingMessage = "";
    private OnvifConnection onvifConnection;

    OnvifCodec(OnvifConnection onvifConnection) {
        this.onvifConnection = onvifConnection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                incomingMessage += content.content().toString(CharsetUtil.UTF_8);
            }
            if (msg instanceof LastHttpContent) {
                onvifConnection.processReply(incomingMessage);
                ctx.close();
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (ctx == null || cause == null) {
            return;
        }
        log.debug("Exception on ONVIF connection: {}", cause.getMessage());
        ctx.close();
    }
}

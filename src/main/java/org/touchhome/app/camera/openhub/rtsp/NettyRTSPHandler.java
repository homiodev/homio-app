package org.touchhome.app.camera.openhub.rtsp;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Decode RTSP traffic into message Strings.
 */
public class NettyRTSPHandler extends ChannelDuplexHandler {
    RTSPConnection rtspConnection;

    NettyRTSPHandler(RTSPConnection rtspConnection) {
        this.rtspConnection = rtspConnection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        if (!(msg instanceof LastHttpContent)) {
            rtspConnection.processMessage(msg);
        } else {
            ctx.close();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    }
}

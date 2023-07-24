package org.homio.addon.tuya.internal.local.handlers;

import java.io.IOException;
import java.util.Objects;


import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * The {@link UserEventHandler} is a Netty handler for events (used for closing the connection)
 */
@Log4j2
public class UserEventHandler extends ChannelDuplexHandler {
    private final String deviceId;

    public UserEventHandler(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof DisposeEvent) {
            log.debug("{}{}: Received DisposeEvent, closing channel", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        if (cause instanceof IOException) {
            log.debug("{}{}: IOException caught, closing channel.", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), cause);
            log.debug("IOException caught: ", cause);
        } else {
            log.warn("{}{}: {} caught, closing the channel", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), cause.getClass(), cause);
        }
        ctx.close();
    }

    public static class DisposeEvent {
    }
}

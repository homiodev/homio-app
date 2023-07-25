package org.homio.addon.tuya.internal.local.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * The {@link UserEventHandler} is a Netty handler for events (used for closing the connection)
 */
@Log4j2
@RequiredArgsConstructor
public class UserEventHandler extends ChannelDuplexHandler {

    private final String deviceId;
    private final String entityID;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof DisposeEvent) {
            log.debug("[{}]: {}{}: Received DisposeEvent, closing channel", entityID, deviceId,
                Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        if (cause instanceof IOException) {
            log.debug("[{}]: {}{}: IOException caught, closing channel.", entityID, deviceId,
                Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), cause);
            log.debug("[{}]: IOException caught: ", entityID, cause);
        } else {
            log.warn("[{}]: {}{}: {} caught, closing the channel", entityID, deviceId,
                Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), cause.getClass(), cause);
        }
        ctx.close();
    }

    public static class DisposeEvent {
    }
}

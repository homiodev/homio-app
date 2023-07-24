package org.homio.addon.tuya.internal.local.handlers;

import static org.homio.addon.tuya.internal.TuyaBindingConstants.TCP_CONNECTION_MAXIMUM_MISSED_HEARTBEATS;
import static org.homio.addon.tuya.internal.TuyaBindingConstants.TCP_CONNECTION_TIMEOUT;
import static org.homio.addon.tuya.internal.local.CommandType.HEART_BEAT;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.local.MessageWrapper;

/**
 * The {@link HeartbeatHandler} is responsible for sending and receiving heartbeat messages
 */
@Log4j2
public class HeartbeatHandler extends ChannelDuplexHandler {
    private final String deviceId;
    private int heartBeatMissed = 0;

    public HeartbeatHandler(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        if (evt instanceof IdleStateEvent e) {
            if (IdleState.READER_IDLE.equals(e.state())) {
                log.warn("{}{}: Did not receive a message from for {} seconds. Connection seems to be dead.",
                    deviceId, Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""),
                    TCP_CONNECTION_TIMEOUT);
                ctx.close();
            } else if (IdleState.WRITER_IDLE.equals(e.state())) {
                heartBeatMissed++;
                if (heartBeatMissed > TCP_CONNECTION_MAXIMUM_MISSED_HEARTBEATS) {
                    log.warn("{}{}: Missed more than {} heartbeat responses. Connection seems to be dead.", deviceId,
                        Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""),
                        TCP_CONNECTION_MAXIMUM_MISSED_HEARTBEATS);
                    ctx.close();
                } else {
                    log.trace("{}{}: Sending ping", deviceId,
                        Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
                    ctx.channel().writeAndFlush(new MessageWrapper<>(HEART_BEAT, Map.of("dps", "")));
                }
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof MessageWrapper<?> m) {
            if (HEART_BEAT.equals(m.commandType)) {
                log.trace("{}{}: Received pong", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
                heartBeatMissed = 0;
                // do not forward HEART_BEAT messages
                ctx.fireChannelReadComplete();
                return;
            }
        }
        // forward to next handler
        ctx.fireChannelRead(msg);
    }
}

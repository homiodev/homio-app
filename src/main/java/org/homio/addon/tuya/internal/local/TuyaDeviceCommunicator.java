package org.homio.addon.tuya.internal.local;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.homio.addon.tuya.internal.local.CommandType.CONTROL;
import static org.homio.addon.tuya.internal.local.CommandType.CONTROL_NEW;
import static org.homio.addon.tuya.internal.local.CommandType.DP_QUERY;
import static org.homio.addon.tuya.internal.local.CommandType.DP_REFRESH;
import static org.homio.addon.tuya.internal.local.CommandType.SESS_KEY_NEG_START;
import static org.homio.addon.tuya.internal.local.ProtocolVersion.V3_4;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.internal.local.handlers.HeartbeatHandler;
import org.homio.addon.tuya.internal.local.handlers.TuyaDecoder;
import org.homio.addon.tuya.internal.local.handlers.TuyaEncoder;
import org.homio.addon.tuya.internal.local.handlers.TuyaMessageHandler;
import org.homio.addon.tuya.internal.local.handlers.UserEventHandler;
import org.homio.addon.tuya.internal.util.CryptoUtil;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Status;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles the device connection
 */
@Log4j2
public class TuyaDeviceCommunicator implements ChannelFutureListener {

    private static final int TCP_CONNECTION_HEARTBEAT_INTERVAL = 10; // in s
    public static final int TCP_CONNECTION_TIMEOUT = 60; // in s;
    public static final int TCP_CONNECTION_MAXIMUM_MISSED_HEARTBEATS = 3;

    private final Bootstrap bootstrap = new Bootstrap();
    private final DeviceStatusListener deviceStatusListener;
    private final String deviceId;

    private final String address;
    private final ProtocolVersion protocolVersion;
    private final TuyaDeviceEntity entity;
    private final KeyStore keyStore;
    private @Nullable Channel channel;

    public TuyaDeviceCommunicator(DeviceStatusListener deviceStatusListener, EventLoopGroup eventLoopGroup,
        String address, String protocolVersion, TuyaDeviceEntity entity) {
        this.address = address;
        this.deviceId = entity.getIeeeAddress();
        this.keyStore = new KeyStore(entity.getLocalKey().getBytes(UTF_8));
        this.deviceStatusListener = deviceStatusListener;
        this.protocolVersion = ProtocolVersion.fromString(protocolVersion);
        this.entity = entity;
        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("idleStateHandler",
                    new IdleStateHandler(TCP_CONNECTION_TIMEOUT, TCP_CONNECTION_HEARTBEAT_INTERVAL, 0));
                pipeline.addLast("messageEncoder",
                    new TuyaEncoder(deviceId, entity.getEntityID(), keyStore, TuyaDeviceCommunicator.this.protocolVersion));
                pipeline.addLast("messageDecoder",
                    new TuyaDecoder(deviceId, entity.getEntityID(), keyStore, TuyaDeviceCommunicator.this.protocolVersion));
                pipeline.addLast("heartbeatHandler", new HeartbeatHandler(deviceId, entity.getEntityID()));
                pipeline.addLast("deviceHandler", new TuyaMessageHandler(deviceId, entity.getEntityID(), keyStore, deviceStatusListener));
                pipeline.addLast("userEventHandler", new UserEventHandler(deviceId, entity.getEntityID()));
            }
        });
        connect();
    }

    public void connect() {
        entity.setStatus(Status.INITIALIZE);
        keyStore.reset(); // reset session key
        bootstrap.connect(address, 6668).addListener(this);
    }

    public ActionResponseModel sendCommand(@NotNull Map<Integer, @Nullable Object> command) {
        CommandType commandType = (protocolVersion == V3_4) ? CONTROL_NEW : CONTROL;
        MessageWrapper<?> m = new MessageWrapper<>(commandType, Map.of("dps", command));
        Channel channel = this.channel;
        if (channel != null) {
            channel.writeAndFlush(m);
            return ActionResponseModel.fired();
        } else {
            log.warn("[{}]: {}: Setting {} failed. Device is not connected.",
                entity.getEntityID(), deviceId, command);
            return ActionResponseModel.showWarn("Device offline");
        }
    }

    public void requestStatus() {
        MessageWrapper<?> m = new MessageWrapper<>(DP_QUERY, Map.of("dps", Map.of()));
        Channel channel = this.channel;
        if (channel != null) {
            channel.writeAndFlush(m);
        } else {
            log.warn("[{}]: {}: Querying status failed. Device is not connected.", entity.getEntityID(), deviceId);
        }
    }

    public void refreshStatus() {
        MessageWrapper<?> m = new MessageWrapper<>(DP_REFRESH, Map.of("dpId", List.of(4, 5, 6, 18, 19, 20)));
        Channel channel = this.channel;
        if (channel != null) {
            channel.writeAndFlush(m);
        } else {
            log.warn("[{}]: {}: Refreshing status failed. Device is not connected.",
                entity.getEntityID(), deviceId);
        }
    }

    public void dispose() {
        Channel channel = this.channel;
        if (channel != null) { // if channel == null we are not connected anyway
            channel.pipeline().fireUserEventTriggered(new UserEventHandler.DisposeEvent());
            this.channel = null;
        }
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) {
        if (channelFuture.isSuccess()) {
            Channel channel = channelFuture.channel();
            this.channel = channel;
            if (protocolVersion == V3_4) {
                // handshake for session key required
                MessageWrapper<?> m = new MessageWrapper<>(SESS_KEY_NEG_START, keyStore.getRandom());
                channel.writeAndFlush(m);
            } else {
                // no handshake for 3.1/3.3
                requestStatus();
            }
        } else {
            String message = Objects.requireNonNullElse(channelFuture.cause().getMessage(), "");
            log.warn("[{}]: {}{}: Failed to connect: {}", entity.getEntityID(), deviceId,
                Objects.requireNonNullElse(channelFuture.channel().remoteAddress(), ""),
                channelFuture.cause().getMessage());
            this.channel = null;
            deviceStatusListener.onDisconnected(message);
        }
    }

    @Getter
    public static class KeyStore {
        private final byte[] deviceKey;
        @Setter
        private byte[] sessionKey;
        private byte[] random;

        public KeyStore(byte[] deviceKey) {
            this.deviceKey = deviceKey;
            this.reset();
        }

        public void reset() {
            this.sessionKey = this.deviceKey;
            this.random = CryptoUtil.generateRandom(16).clone();
        }
    }
}

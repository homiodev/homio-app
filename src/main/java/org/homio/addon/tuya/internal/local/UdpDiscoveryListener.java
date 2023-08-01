package org.homio.addon.tuya.internal.local;

import static com.sshtools.common.util.Utils.hexToBytes;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.local.handlers.DatagramToByteBufDecoder;
import org.homio.addon.tuya.internal.local.handlers.DiscoveryMessageHandler;
import org.homio.addon.tuya.internal.local.handlers.TuyaDecoder;
import org.homio.addon.tuya.internal.local.handlers.UserEventHandler;
import org.homio.addon.tuya.internal.util.CryptoUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Handles UDP device discovery message
 */
@Log4j2
@RequiredArgsConstructor
public class UdpDiscoveryListener implements ChannelFutureListener {

    private static final byte[] TUYA_UDP_KEY = hexToBytes(CryptoUtil.md5("yGAdlopoPVldABfn"));

    private final Map<String, DeviceInfo> deviceInfos = new ConcurrentHashMap<>();
    private final Map<String, DeviceInfoSubscriber> deviceListeners = new ConcurrentHashMap<>();

    private Channel encryptedChannel;
    private Channel rawChannel;
    private final EventLoopGroup group;
    private boolean deactivate = false;

    @Setter
    private String projectEntityID;

    public void activate() {
        try {
            log.info("[{}]: Activate udp device ip address discovery", projectEntityID);
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class).option(ChannelOption.SO_BROADCAST, true)
             .handler(new ChannelInitializer<DatagramChannel>() {
                 @Override
                 protected void initChannel(DatagramChannel ch) {
                     ChannelPipeline pipeline = ch.pipeline();
                     pipeline.addLast("udpDecoder", new DatagramToByteBufDecoder());
                     pipeline.addLast("messageDecoder", new TuyaDecoder("udpListener", "",
                         new TuyaDeviceCommunicator.KeyStore(TUYA_UDP_KEY), ProtocolVersion.V3_1));
                     pipeline.addLast("discoveryHandler",
                         new DiscoveryMessageHandler(deviceInfos, deviceListeners));
                     pipeline.addLast("userEventHandler", new UserEventHandler("udpListener", ""));
                 }
             });

            ChannelFuture futureEncrypted = b.bind(6667).addListener(this).sync();
            encryptedChannel = futureEncrypted.channel();

            ChannelFuture futureRaw = b.bind(6666).addListener(this).sync();
            rawChannel = futureRaw.channel();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deactivate() {
        log.warn("[{}]: Close udp device ip address discovery", projectEntityID);
        deactivate = true;
        encryptedChannel.pipeline().fireUserEventTriggered(new UserEventHandler.DisposeEvent());
        rawChannel.pipeline().fireUserEventTriggered(new UserEventHandler.DisposeEvent());
        try {
            encryptedChannel.closeFuture().sync();
            rawChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    public void registerListener(String deviceId, DeviceInfoSubscriber subscriber) {
        if (deviceListeners.put(deviceId, subscriber) != null) {
            log.warn("[{}]: Registered a second listener for '{}'.", projectEntityID, deviceId);
        }
        DeviceInfo deviceInfo = deviceInfos.get(deviceId);
        if (deviceInfo != null) {
            subscriber.deviceInfoChanged(deviceInfo);
        }
    }

    public void unregisterListener(@Nullable String deviceId) {
        if (deviceId != null) {
            deviceListeners.remove(deviceId);
            deviceInfos.remove(deviceId);
        }
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) {
        if (!channelFuture.isSuccess() && !deactivate) {
            // if we are not disposing, restart listener after an error
            deactivate();
            activate();
        }
    }
}

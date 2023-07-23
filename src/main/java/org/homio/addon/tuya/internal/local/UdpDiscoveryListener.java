package org.homio.addon.tuya.internal.local;

import static com.sshtools.common.util.Utils.hexToBytes;

import java.util.HashMap;
import java.util.Map;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.local.handlers.DatagramToByteBufDecoder;
import org.homio.addon.tuya.internal.local.handlers.TuyaDecoder;
import org.homio.addon.tuya.internal.local.handlers.UserEventHandler;
import org.homio.addon.tuya.internal.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
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

/**
 * The {@link UdpDiscoveryListener} handles UDP device discovery message
 */
public class UdpDiscoveryListener implements ChannelFutureListener {
    private static final byte[] TUYA_UDP_KEY = hexToBytes(CryptoUtil.md5("yGAdlopoPVldABfn"));

    private final Logger logger = LoggerFactory.getLogger(UdpDiscoveryListener.class);

    private final Gson gson = new Gson();

    private final Map<String, DeviceInfo> deviceInfos = new HashMap<>();
    private final Map<String, DeviceInfoSubscriber> deviceListeners = new HashMap<>();

    private Channel encryptedChannel;
    private Channel rawChannel;
    private final EventLoopGroup group;
    private boolean deactivate = false;

    public UdpDiscoveryListener(EventLoopGroup group) {
        this.group = group;
        activate();
    }

    private void activate() {
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class).option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("udpDecoder", new DatagramToByteBufDecoder());
                            pipeline.addLast("messageDecoder", new TuyaDecoder(gson, "udpListener",
                                    new TuyaDevice.KeyStore(TUYA_UDP_KEY), ProtocolVersion.V3_1));
                            pipeline.addLast("discoveryHandler",
                                    new DiscoveryMessageHandler(deviceInfos, deviceListeners));
                            pipeline.addLast("userEventHandler", new UserEventHandler("udpListener"));
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
            log.warn("Registered a second listener for '{}'.", deviceId);
        }
        DeviceInfo deviceInfo = deviceInfos.get(deviceId);
        if (deviceInfo != null) {
            subscriber.deviceInfoChanged(deviceInfo);
        }
    }

    public void unregisterListener(DeviceInfoSubscriber deviceInfoSubscriber) {
        if (!deviceListeners.entrySet().removeIf(e -> deviceInfoSubscriber.equals(e.getValue()))) {
            log.warn("Tried to unregister a listener for '{}' but no registration found.", deviceInfoSubscriber);
        }
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (!channelFuture.isSuccess() && !deactivate) {
            // if we are not disposing, restart listener after an error
            deactivate();
            activate();
        }
    }
}

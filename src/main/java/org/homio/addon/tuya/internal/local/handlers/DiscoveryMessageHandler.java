package org.homio.addon.tuya.internal.local.handlers;

import static org.homio.addon.tuya.internal.local.CommandType.BROADCAST_LPV34;
import static org.homio.addon.tuya.internal.local.CommandType.UDP;
import static org.homio.addon.tuya.internal.local.CommandType.UDP_NEW;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.homio.addon.tuya.internal.local.DeviceInfoSubscriber;
import org.homio.addon.tuya.internal.local.MessageWrapper;
import org.homio.addon.tuya.internal.local.dto.DeviceInfo;
import org.homio.addon.tuya.internal.local.dto.DiscoveryMessage;

/**
 * The {@link DiscoveryMessageHandler} is used for handling UDP discovery messages
 */
@RequiredArgsConstructor
public class DiscoveryMessageHandler extends ChannelDuplexHandler {
    private final Map<String, DeviceInfo> deviceInfos;
    private final Map<String, DeviceInfoSubscriber> deviceListeners;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof MessageWrapper<?> mw) {
            if ((mw.commandType == UDP_NEW || mw.commandType == UDP || mw.commandType == BROADCAST_LPV34)) {
                DiscoveryMessage discoveryMessage = (DiscoveryMessage) Objects.requireNonNull(mw.content);
                DeviceInfo deviceInfo = new DeviceInfo(discoveryMessage.ip, discoveryMessage.version);
                if (!deviceInfo.equals(deviceInfos.put(discoveryMessage.deviceId, deviceInfo))) {
                    DeviceInfoSubscriber subscriber = deviceListeners.get(discoveryMessage.deviceId);

                    if (subscriber != null) {
                        subscriber.deviceInfoChanged(deviceInfo);
                    }
                }
            }
        }
    }
}

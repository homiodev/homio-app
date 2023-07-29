package org.homio.addon.tuya.internal.local.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Netty Decoder for UDP messages
 */
public class DatagramToByteBufDecoder extends MessageToMessageDecoder<DatagramPacket> {

    @Override
    protected void decode(@Nullable ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
        out.add(msg.content().copy());
    }
}

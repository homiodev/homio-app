package org.touchhome.bundle.cloud.netty.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class SocketModelEncoder extends MessageToByteEncoder<SocketBaseModel> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SocketBaseModel socketBaseModel, ByteBuf out) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(socketBaseModel);

        out.writeInt(bytes.length);
        out.writeByte(ModelType.getType(socketBaseModel).ordinal());
        out.writeBytes(bytes);
    }
}

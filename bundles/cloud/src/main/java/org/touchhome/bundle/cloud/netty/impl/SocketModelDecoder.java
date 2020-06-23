package org.touchhome.bundle.cloud.netty.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

public class SocketModelDecoder extends ReplayingDecoder<SocketBaseModel> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private static ModelType getModelType(byte type) {
        if (ModelType.values().length < type) {
            throw new RuntimeException("Unable to find correct model type for type: " + type);
        }
        return ModelType.values()[type];
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf buffer, List<Object> out) throws Exception {

        int length = buffer.readInt();
        ModelType modelType = getModelType(buffer.readByte());
        byte[] dst = new byte[length];
        buffer.readBytes(dst);

        out.add(objectMapper.readValue(dst, modelType.getTargetClass()));
    }
}

package org.touchhome.bundle.cloud.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class ClientProcessingHandler extends ChannelInboundHandlerAdapter {

    private final DispatcherServletService dispatcherServletService;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.info("Handler added");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("Handler removed");
    }

    @Override
    @SneakyThrows
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        SocketBaseModel socketBaseModel = (SocketBaseModel) msg;
        log.info("Client netty received: " + socketBaseModel);

        SocketBaseModel responseModel = null;
        if (socketBaseModel instanceof SocketPingRequestModel) {
            responseModel = new SocketPingResponseModel();
        } else if (socketBaseModel instanceof SocketRestRequestModel) {
            responseModel = this.handleSocketRestRequest((SocketRestRequestModel) socketBaseModel);
        }

        if (responseModel == null) {
            throw new RuntimeException("No handler found for model: " + msg);
        }

        ctx.writeAndFlush(responseModel).sync();
    }

    private SocketRestResponseModel handleSocketRestRequest(SocketRestRequestModel model) {
        try {
            return dispatcherServletService.doService(model);
        } catch (Exception ex) {
            log.error("Socket dispatcher error while execute request");
            return SocketRestResponseModel.ofError(model.getRequestId(), ex);
        }
    }
}

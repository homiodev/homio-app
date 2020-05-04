package org.touchhome.bundle.cloud.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionMessageSetting;
import org.touchhome.bundle.cloud.setting.CloudServerConnectionStatusSetting;
import org.touchhome.bundle.cloud.setting.CloudServerRestartSetting;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
@Component
@RequiredArgsConstructor
public class NettyClientService {

    private final DispatcherServletService dispatcherServletService;
    private final EntityContext entityContext;
    @Value("${serverPort:8888}")
    private int serverPort;
    @Value("${serverHost:localhost}")
    private String serverHost;
    @Value("${serverUseSSl:true}")
    private boolean serverUseSSl;
    private EventLoopGroup workGroup = new NioEventLoopGroup();
    private Thread listenClientsThread;

    private ServerConnectionStatus serverConnectionStatus;

    public void postConstruct() {
        updateConnectionStatus(ServerConnectionStatus.NOT_CONNECTED, "");
        connectToServer();
        this.entityContext.listenSettingValue(CloudServerRestartSetting.class, this::restart);
    }

    private void restart() {
        log.info("Start/restart connection to cloud");
        if (serverConnectionStatus == ServerConnectionStatus.CONNECTED) {
            try {
                this.workGroup.shutdownGracefully().get(60, TimeUnit.SECONDS);
                this.connectToServer();
            } catch (TimeoutException ex) {
                log.error("Unable to finish connection in 60 seconds");
            } catch (Exception ex) {
                log.error("Error stopping connect to server", ex);
            }
        } else {
            this.connectToServer();
        }
    }

    private void connectToServer() {
        UserEntity user = entityContext.getEntity(UserRepository.DEFAULT_USER_ID);
        if (user.getPassword() == null) {
            updateConnectionStatus(serverConnectionStatus, "CLOUD.USER_HAS_NO_PASSWORD");
            log.warn("Unable start server discovering. User password is empty");
            return;
        }
        if (user.getKeystore() == null) {
            updateConnectionStatus(serverConnectionStatus, "CLOUD.USER_HAS_NO_KEYSTORE");
            log.warn("Unable start server discovering. User keystore is empty");
            return;
        }
        this.listenClientsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                log.info("Starting netty client");
                try {
                    connectLoop();
                    updateConnectionStatus(ServerConnectionStatus.DISCONNECTED, "");
                } catch (Exception ex) {
                    log.error("Netty client finished with error", ex);
                    updateConnectionStatus(ServerConnectionStatus.DISCONNECTED_WIDTH_ERRORS, TouchHomeUtils.getErrorMessage(ex));
                }
             /*   try {
                    workGroup.shutdownGracefully().get();
                } catch (Exception ex) {
                    log.error("Unable to finish workgroup", ex);
                    throw new RuntimeException(ex);
                }*/
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ignore) {
                }
            }
            log.error("Netty client finished");
        });
        listenClientsThread.setName("Thread - netty");
        listenClientsThread.start();
    }

    @SneakyThrows
    private void connectLoop() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel socketChannel) throws Exception {
                log.info("Client init channel: <{}>", socketChannel.toString());
                ChannelPipeline pipeline = socketChannel.pipeline();

                if (NettyClientService.this.serverUseSSl) {
                    UserEntity user = entityContext.getEntity(UserRepository.DEFAULT_USER_ID);
                    SSLContext sslContext = SslUtil.createSSLContext(user.getKeystore(), user.getPassword());
                    SSLEngine engine = sslContext.createSSLEngine(
                            NettyClientService.this.serverHost,
                            NettyClientService.this.serverPort);
                    engine.setEnabledProtocols(new String[]{"TLSv1.2"});
                    engine.setUseClientMode(true);

                    pipeline.addLast(new SslHandler(engine, false));
                }

                pipeline.addLast(
                        new SocketModelEncoder(),
                        new SocketModelDecoder(),
                        new ClientProcessingHandler(dispatcherServletService));
            }
        });
        ChannelFuture channelFuture = bootstrap.connect(NettyClientService.this.serverHost, NettyClientService.this.serverPort).sync();
        updateConnectionStatus(ServerConnectionStatus.CONNECTED, "");
        log.info("Netty client started");

        ChannelFuture future = channelFuture.channel().closeFuture().sync();

        future.get();
    }

    private void updateConnectionStatus(ServerConnectionStatus serverConnectionStatus, String errorStatus) {
        this.serverConnectionStatus = serverConnectionStatus;
        this.entityContext.setSettingValue(CloudServerConnectionMessageSetting.class, errorStatus);
        this.entityContext.setSettingValue(CloudServerConnectionStatusSetting.class, serverConnectionStatus);
    }
}

package org.touchhome.app.videoStream.scanner;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.videoStream.CameraCoordinator;
import org.touchhome.app.videoStream.entity.RtspVideoStreamEntity;
import org.touchhome.app.videoStream.rtsp.message.sdp.SdpMessage;
import org.touchhome.app.videoStream.rtsp.message.sdp.SdpParser;
import org.touchhome.app.videoStream.setting.rtsp.RtspDiscoveryIpAddressPingTimeoutSetting;
import org.touchhome.app.videoStream.setting.rtsp.RtspScanPortsSetting;
import org.touchhome.app.videoStream.setting.rtsp.RtspScanUrlsSetting;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.model.ProgressBar;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.scan.BaseItemsDiscovery;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.netty.handler.codec.rtsp.RtspDecoder.DEFAULT_MAX_CONTENT_LENGTH;
import static io.netty.handler.codec.rtsp.RtspHeaderNames.CONTENT_BASE;

@Log4j2
@Component
@RequiredArgsConstructor
public class RtspStreamScanner implements VideoStreamScanner {

    public static final AttributeKey<URI> URL = AttributeKey.valueOf("url");
    private final CameraCoordinator cameraCoordinator;
    private final EntityContext entityContext;

    private static final int THREAD_COUNT = 8;
    private static final int BOOTSTRAP_AWAIT_TERMINATION_SEC = 60;
    private int cseq = 1;

    private Map<String, RtspVideoStreamEntity> existsRtspStreamEntity;
    private BaseItemsDiscovery.DeviceScannerResult result;
    private String headerConfirmButtonKey;
    private Bootstrap bootstrap;
    private BiConsumer<String, SdpMessage> rtspAliveHandler;

    @Override
    public String getName() {
        return "scan-rtsp-stream";
    }

    public synchronized void scan(List<RtspVideoStreamEntity> rtspStreamEntities) throws InterruptedException {
        this.existsRtspStreamEntity = rtspStreamEntities.stream().collect(Collectors.toMap(RtspVideoStreamEntity::getIeeeAddress, Function.identity()));
        this.rtspAliveHandler = PING_HANDLER;

        NioEventLoopGroup mainEventLoopGroup = reCreateBootstrap();
        for (RtspVideoStreamEntity rtspVideoStreamEntity : this.existsRtspStreamEntity.values()) {
            try {
                URI uri = URI.create(rtspVideoStreamEntity.getIeeeAddress());
                bootstrap.attr(URL, uri).connect(uri.getHost(), uri.getPort()).addListener(ON_CONNECTED);
            } catch (Exception ex) {
                log.warn("Unable to check rtsp url: <{}>. Msg: {}", rtspVideoStreamEntity.getIeeeAddress(), ex.getMessage());
            }
        }
        if (bootstrap != null) {
            mainEventLoopGroup.awaitTermination(BOOTSTRAP_AWAIT_TERMINATION_SEC, TimeUnit.SECONDS);
            mainEventLoopGroup.shutdownGracefully().sync();
            bootstrap = null;
        }
    }

    @SneakyThrows
    @Override
    public synchronized BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        this.headerConfirmButtonKey = headerConfirmButtonKey;
        this.result = new BaseItemsDiscovery.DeviceScannerResult();
        this.existsRtspStreamEntity = entityContext.findAll(RtspVideoStreamEntity.class)
                .stream().collect(Collectors.toMap(RtspVideoStreamEntity::getIeeeAddress, Function.identity()));
        this.rtspAliveHandler = DISCOVERY_HANDLER;

        NioEventLoopGroup mainEventLoopGroup = reCreateBootstrap();
        NetworkHardwareRepository networkHardwareRepository = entityContext.getBean(NetworkHardwareRepository.class);

        Set<Integer> ports = entityContext.setting().getValue(RtspScanPortsSetting.class);
        int pingTimeout = entityContext.setting().getValue(RtspDiscoveryIpAddressPingTimeoutSetting.class);
        Set<String> urls = entityContext.setting().getValue(RtspScanUrlsSetting.class);

        Map<String, Callable<Integer>> tasks = networkHardwareRepository.buildPingIpAddressTasks(log, ports, pingTimeout, (ipAddress, port) -> {
            log.info("Rtsp ip alive: <{}:{}>. Send 'DESCRIBE' request to all possible urls {}...", ipAddress, port, urls.size());
            for (String url : urls) {
                String rtspURL = "rtsp://" + ipAddress + ":" + port + url.trim();
                try {
                    // create separate connection for each rtsp url, because remove host may close connection on their side.
                    bootstrap.attr(URL, URI.create(rtspURL)).connect(ipAddress, port).addListener(ON_CONNECTED);
                } catch (Exception ex) {
                    log.warn("Unable to check rtsp url: <{}>. Msg: {}", rtspURL, ex.getMessage());
                }
            }
        });
        List<Integer> availableRtspAddresses = entityContext.bgp().runInBatchAndGet("scan-rtsp-batch-result",
                pingTimeout * tasks.size() / 1000, THREAD_COUNT, tasks,
                completedTaskCount -> progressBar.progress(100 / (float) tasks.size() * completedTaskCount,
                        "Rtsp stream done " + completedTaskCount + "/" + tasks.size() + " tasks"));

        if (bootstrap != null) {
            // TODO: mainEventLoopGroup.awaitTermination(10, TimeUnit.SECONDS);
            mainEventLoopGroup.shutdownGracefully().sync();
            bootstrap = null;
        }
        log.info("Found {} rtcp streams", availableRtspAddresses.size());

        return result;
    }

    private NioEventLoopGroup reCreateBootstrap() {
        NioEventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();

        this.bootstrap = new Bootstrap()
                .group(mainEventLoopGroup)
                .channel(NioSocketChannel.class);
        this.bootstrap.option(ChannelOption.SO_RCVBUF, 131072);
        this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        this.bootstrap.handler(new ClientChannelInitializer());

        return mainEventLoopGroup;
    }

    @RequiredArgsConstructor
    private class NettyRtspChannelHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final URI uri;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            int requestCseq = cseq++;
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, uri.toString());
            request.headers().add(RtspHeaderNames.CSEQ, requestCseq);
            ctx.writeAndFlush(request);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            URI uri = URI.create(msg.headers().get(CONTENT_BASE));
            SdpMessage sdpMessage = new SdpParser().parse(msg.content().toString(CharsetUtil.UTF_8));
            String uriStr = uri.toString();

            if (msg.status().equals(HttpResponseStatus.OK)) {
                log.info("Found alive RTSP with url: <{}>. Describe message: <{}>", uri, sdpMessage);
                cameraCoordinator.setSdpMessage(uriStr, sdpMessage);
                rtspAliveHandler.accept(uriStr, sdpMessage);
            } else {
                cameraCoordinator.removeSpdMessage(uriStr);
            }
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.channel().close();
        }
    }

    private class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            URI uri = ch.attr(URL).get();
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new IdleStateHandler(0, 0, 5000, TimeUnit.MILLISECONDS));
            pipeline.addLast(new RtspDecoder());
            pipeline.addLast(new RtspEncoder());
            pipeline.addLast(new HttpObjectAggregator(DEFAULT_MAX_CONTENT_LENGTH));
            pipeline.addLast(new NettyRtspChannelHandler(uri));
        }
    }

    private final ChannelFutureListener ON_CONNECTED = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                log.debug("Channel connected: {}", channel);
                channel.closeFuture().addListener(ON_CLOSED);
            } else {
                log.warn("Unable connect to {}. Error: {}", future.channel().attr(URL).get(), future);
            }
        }
    };

    private final ChannelFutureListener ON_CLOSED = future -> log.debug("Channel closed: {}", future.channel());

    private BiConsumer<String, SdpMessage> DISCOVERY_HANDLER = new BiConsumer<String, SdpMessage>() {
        @Override
        public void accept(String uriStr, SdpMessage sdpMessage) {
            if (!existsRtspStreamEntity.containsKey(uriStr)) {
                result.getNewCount().incrementAndGet();
                handleDevice(headerConfirmButtonKey, "rtsp-" + uriStr.hashCode(), sdpMessage.getSessionName(), entityContext,
                        messages -> {
                            messages.add(Lang.getServerMessage("NEW_DEVICE.NAME", "NAME", sdpMessage.getSessionName()));
                            messages.add(Lang.getServerMessage("NEW_DEVICE.URL", "URL", uriStr));
                        },
                        () -> {
                            log.info("Confirm save rtsp stream entity: <{}>", sdpMessage.getSessionName());
                            entityContext.save(new RtspVideoStreamEntity().setIeeeAddress(uriStr));
                        });
            } else {
                result.getExistedCount().incrementAndGet();
            }
        }
    };

    private BiConsumer<String, SdpMessage> PING_HANDLER = new BiConsumer<String, SdpMessage>() {
        @Override
        public void accept(String uriStr, SdpMessage sdpMessage) {
            RtspVideoStreamEntity rtspVideoStreamEntity = existsRtspStreamEntity.get(uriStr);
            if (rtspVideoStreamEntity != null) {
                entityContext.updateDelayed(rtspVideoStreamEntity, e -> e.setStatus(Status.ONLINE));
            }
        }
    };
}

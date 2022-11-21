package org.touchhome.app.service;

import static io.netty.handler.codec.rtsp.RtspDecoder.DEFAULT_MAX_CONTENT_LENGTH;
import static io.netty.handler.codec.rtsp.RtspHeaderNames.CONTENT_BASE;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.touchhome.app.model.entity.CommonVideoStreamEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.scan.BaseItemsDiscovery;
import org.touchhome.bundle.api.service.scan.VideoStreamScanner;
import org.touchhome.bundle.camera.rtsp.message.sdp.SdpMessage;
import org.touchhome.bundle.camera.rtsp.message.sdp.SdpParser;
import org.touchhome.bundle.camera.setting.CameraScanPortRangeSetting;
import org.touchhome.bundle.camera.setting.rtsp.ScanRtspIpAddressMaxPingTimeoutSetting;
import org.touchhome.bundle.camera.setting.rtsp.ScanRtspPortsSetting;
import org.touchhome.bundle.camera.setting.rtsp.ScanRtspUrlsSetting;
import org.touchhome.common.model.ProgressBar;
import org.touchhome.common.util.Lang;

@Log4j2
@Component
@RequiredArgsConstructor
public class RtspStreamScanner implements VideoStreamScanner {

  public static Map<String, SdpMessage> rtspUrlToSdpMessage = new ConcurrentHashMap<>();

  public static final AttributeKey<URI> URL = AttributeKey.valueOf("url");
  private static final int THREAD_COUNT = 8;
  private static final int BOOTSTRAP_AWAIT_TERMINATION_SEC = 60;
  private final EntityContext entityContext;
  private final ChannelFutureListener ON_CLOSED = future -> log.debug("Channel closed: {}", future.channel());
  private final ChannelFutureListener ON_CONNECTED = future -> {
    if (future.isSuccess()) {
      Channel channel = future.channel();
      log.debug("Channel connected: {}", channel);
      channel.closeFuture().addListener(ON_CLOSED);
    } else {
      log.warn("Unable connect to {}. Error: {}", future.channel().attr(URL).get(), future);
    }
  };
  private int cseq = 1;
  private Map<String, CommonVideoStreamEntity> existsRtspStreamEntity;
  private final BiConsumer<String, SdpMessage> PING_HANDLER = new BiConsumer<>() {
    @Override
    public void accept(String uriStr, SdpMessage sdpMessage) {
      CommonVideoStreamEntity commonVideoStreamEntity = existsRtspStreamEntity.get(uriStr);
      if (commonVideoStreamEntity != null) {
        entityContext.updateDelayed(commonVideoStreamEntity, e -> e.setStatus(Status.WAITING));
      }
    }
  };
  private BaseItemsDiscovery.DeviceScannerResult result;
  private String headerConfirmButtonKey;
  private final BiConsumer<String, SdpMessage> DISCOVERY_HANDLER = new BiConsumer<>() {
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
              entityContext.save(new CommonVideoStreamEntity().setIeeeAddress(uriStr));
            });
      } else {
        result.getExistedCount().incrementAndGet();
      }
    }
  };
  private Bootstrap bootstrap;
  private BiConsumer<String, SdpMessage> rtspAliveHandler;

  @Override
  public String getName() {
    return "scan-rtsp-stream";
  }

  public synchronized void scan(List<CommonVideoStreamEntity> rtspStreamEntities) throws InterruptedException {
    this.existsRtspStreamEntity = rtspStreamEntities.stream()
        .collect(Collectors.toMap(CommonVideoStreamEntity::getIeeeAddress, Function.identity()));
    this.rtspAliveHandler = PING_HANDLER;

    NioEventLoopGroup mainEventLoopGroup = reCreateBootstrap();
    for (CommonVideoStreamEntity commonVideoStreamEntity : this.existsRtspStreamEntity.values()) {
      try {
        URI uri = URI.create(commonVideoStreamEntity.getIeeeAddress());
        bootstrap.attr(URL, uri).connect(uri.getHost(), uri.getPort()).addListener(ON_CONNECTED);
      } catch (Exception ex) {
        log.warn("Unable to check rtsp url: <{}>. Msg: {}", commonVideoStreamEntity.getIeeeAddress(), ex.getMessage());
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
  public synchronized BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar,
      String headerConfirmButtonKey) {
    this.headerConfirmButtonKey = headerConfirmButtonKey;
    this.result = new BaseItemsDiscovery.DeviceScannerResult();
    this.existsRtspStreamEntity = entityContext.findAll(CommonVideoStreamEntity.class)
        .stream().collect(Collectors.toMap(CommonVideoStreamEntity::getIeeeAddress, Function.identity()));
    this.rtspAliveHandler = DISCOVERY_HANDLER;

    NioEventLoopGroup mainEventLoopGroup = reCreateBootstrap();
    NetworkHardwareRepository networkHardwareRepository = entityContext.getBean(NetworkHardwareRepository.class);

    Set<Integer> ports = entityContext.setting().getValue(ScanRtspPortsSetting.class);
    int pingTimeout = entityContext.setting().getValue(ScanRtspIpAddressMaxPingTimeoutSetting.class);
    Set<String> urls = entityContext.setting().getValue(ScanRtspUrlsSetting.class);
    Set<String> ipRangeList = entityContext.setting().getValue(CameraScanPortRangeSetting.class);

    Map<String, Callable<Integer>> tasks = new HashMap<>();
    for (String ipRange : ipRangeList) {
      tasks.putAll(
          networkHardwareRepository.buildPingIpAddressTasks(ipRange, log, ports, pingTimeout, ipAliveHandler(urls)));
    }

    List<Integer> availableRtspAddresses = entityContext.bgp().runInBatchAndGet("scan-rtsp-batch-result",
        Duration.ofMillis((long) pingTimeout * tasks.size()), THREAD_COUNT, tasks,
        completedTaskCount -> progressBar.progress(100 / (float) tasks.size() * completedTaskCount,
            "Rtsp stream done " + completedTaskCount + "/" + tasks.size() + " tasks"));

    if (bootstrap != null) {
      // TODO: mainEventLoopGroup.awaitTermination(10, TimeUnit.SECONDS);
      mainEventLoopGroup.shutdownGracefully().sync();
      bootstrap = null;
    }
    log.info("Found {} rtcp streams", availableRtspAddresses.stream().filter(Objects::nonNull).count());

    return result;
  }

  @NotNull
  private BiConsumer<String, Integer> ipAliveHandler(Set<String> urls) {
    return (ipAddress, port) -> {
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
    };
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
      DefaultFullHttpRequest request =
          new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, uri.toString());
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
        rtspUrlToSdpMessage.put(uriStr, sdpMessage);
        rtspAliveHandler.accept(uriStr, sdpMessage);
      } else {
        rtspUrlToSdpMessage.remove(uriStr);
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
}

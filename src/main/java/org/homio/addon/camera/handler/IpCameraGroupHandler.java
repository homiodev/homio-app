package org.homio.addon.camera.handler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.onvif.util.GroupTracker;
import org.homio.addon.camera.onvif.util.StreamServerGroupHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.model.Status;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;

/**
 * Not used yet or at all responsible for finding cameras that are part of this group and displaying a group picture.
 */
@Log4j2
@Deprecated
public class IpCameraGroupHandler {

  private final ScheduledExecutorService pollCameraGroup = Executors.newSingleThreadScheduledExecutor();
  public ArrayList<OnvifCameraService> cameraOrder = new ArrayList<>(2);
  //    private WidgetLiveStreamEntity widgetLiveStreamEntity;
  public String hostIp;
  public int serverPort = 0;
  public String playList = "";
  public int cameraIndex = 0;
  public boolean hlsTurnedOn = false;
  private BigDecimal pollTimeInSeconds = new BigDecimal(2);
  private EventLoopGroup serversLoopGroup = new NioEventLoopGroup();
  private ScheduledFuture<?> pollCameraGroupJob = null;
  private ServerBootstrap serverBootstrap;
  private ChannelFuture serverFuture = null;
  private boolean motionChangesOrder = true;
  private String playingNow = "";
  private int entries = 0;
  private int mediaSequence = 1;
  private int discontinuitySequence = 0;
  private GroupTracker groupTracker;

  public IpCameraGroupHandler(/*WidgetLiveStreamEntity widgetLiveStreamEntity,*/ String hostIp, GroupTracker groupTracker) {
    //        this.widgetLiveStreamEntity = widgetLiveStreamEntity;
    this.hostIp = hostIp;
    this.groupTracker = groupTracker;
  }

  public String getPlayList() {
    return playList;
  }

  public String getOutputFolder(int index) {
    OnvifCameraService handle = cameraOrder.get(index);
    return handle.getFfmpegMP4OutputPath().toString();
  }

  private String readCamerasPlaylist(int cameraIndex) {
    String camerasm3u8 = "";
    OnvifCameraService handle = cameraOrder.get(cameraIndex);
    try {
      Path file = handle.getFfmpegHLSOutputPath().resolve("ipcamera.m3u8");
      camerasm3u8 = new String(Files.readAllBytes(file));
    } catch (IOException e) {
      log.warn("[{}]: Error occurred fetching a groupDisplay cameras m3u8 file :{}", handle.getEntityID(), e.getMessage());
    }
    return camerasm3u8;
  }

  String keepLast(String string, int numberToRetain) {
    int start = string.length();
    for (int loop = numberToRetain; loop > 0; loop--) {
      start = string.lastIndexOf("#EXTINF:", start - 1);
      if (start == -1) {
        log.warn(
            "Playlist did not contain enough entries, check all cameras in groups use the same HLS settings.");
        return "";
      }
    }
    entries = entries + numberToRetain;
    return string.substring(start);
  }

  String removeFromStart(String string, int numberToRemove) {
    int startingFrom = string.indexOf("#EXTINF:");
    for (int loop = numberToRemove; loop > 0; loop--) {
      startingFrom = string.indexOf("#EXTINF:", startingFrom + 27);
      if (startingFrom == -1) {
        log.warn(
            "Playlist failed to remove entries from start, check all cameras in groups use the same HLS settings.");
        return string;
      }
    }
    mediaSequence = mediaSequence + numberToRemove;
    entries = entries - numberToRemove;
    return string.substring(startingFrom);
  }

  int howManySegments(String m3u8File) {
    int start = m3u8File.length();
    int numberOfFiles = 0;
    for (BigDecimal totalTime = new BigDecimal(0); totalTime.intValue() < pollTimeInSeconds
        .intValue(); numberOfFiles++) {
      start = m3u8File.lastIndexOf("#EXTINF:", start - 1);
      if (start != -1) {
        totalTime = totalTime.add(new BigDecimal(m3u8File.substring(start + 8, m3u8File.indexOf(",", start))));
      } else {
        log.debug("Group did not find enough segments, lower the poll time if this message continues.");
        break;
      }
    }
    return numberOfFiles;
  }

  public void createPlayList() {
    String m3u8File = readCamerasPlaylist(cameraIndex);
    if (m3u8File.equals("")) {
      return;
    }
    int numberOfSegments = howManySegments(m3u8File);
    log.debug("Using {} segmented files to make up a poll period.", numberOfSegments);
    m3u8File = keepLast(m3u8File, numberOfSegments);
    m3u8File = m3u8File.replace("ipvideo", cameraIndex + "ipvideo"); // add index so we can then fetch output path
    if (entries > numberOfSegments * 3) {
      playingNow = removeFromStart(playingNow, entries - (numberOfSegments * 3));
    }
    playingNow = playingNow + "#EXT-X-DISCONTINUITY\n" + m3u8File;
    playList = "#EXTM3U\n#EXT-X-VERSION:6\n#EXT-X-TARGETDURATION:5\n#EXT-X-ALLOW-CACHE:NO\n#EXT-X-DISCONTINUITY-SEQUENCE:"
        + discontinuitySequence + "\n#EXT-X-MEDIA-SEQUENCE:" + mediaSequence + "\n" + playingNow;
  }

  private IpCameraGroupHandler getHandle() {
    return this;
  }

  @SuppressWarnings("null")
  public void startStreamServer(boolean start) {
    if (!start) {
      serversLoopGroup.shutdownGracefully(8, 8, TimeUnit.SECONDS);
      serverBootstrap = null;
    } else {
      if (serverBootstrap == null) {
        try {
          serversLoopGroup = new NioEventLoopGroup();
          serverBootstrap = new ServerBootstrap();
          serverBootstrap.group(serversLoopGroup);
          serverBootstrap.channel(NioServerSocketChannel.class);
          // IP "0.0.0.0" will bind the server to all network connections//
          serverBootstrap.localAddress(new InetSocketAddress("0.0.0.0", serverPort));
          serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) {
              socketChannel.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 25, 0));
              socketChannel.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
              socketChannel.pipeline().addLast("ChunkedWriteHandler", new ChunkedWriteHandler());
              socketChannel.pipeline().addLast("streamServerHandler",
                  new StreamServerGroupHandler(getHandle()));
            }
          });
          serverFuture = serverBootstrap.bind().sync();
          serverFuture.await(4000);
          log.info("IpCamera file server for a group of cameras has started on port {} for all NIC's.",
              serverPort);
        } catch (Exception e) {
          updateStatus(Status.OFFLINE, "Exception occurred when starting the streaming server. Try changing the serverPort to another " +
              "number.");
        }
      }
    }
  }

  private void updateStatus(Status status, String message) {
    log.info("Update status: <{}>. <{}>", status, message);
  }

  private void updateState(String stateType, State state) {
    log.info("Update stateType: <{}>. State: <{}>", stateType, state.toString());
  }

  // Event based. This is called as each camera comes online after the group handler is registered.
  public void cameraOnline(BaseVideoEntity cameraEntity) {
    log.debug("New camera {} came online, checking if part of this group", cameraEntity.getEntityID());
    //widgetLiveStreamEntity.getSeries().stream().filter(s -> s.getDataSource().equals(cameraEntity.getEntityID()))
    //      .findAny().ifPresent(WidgetVideoSeriesEntity -> {
    //     addCamera(cameraEntity);
    // });
  }

  void addCamera(BaseVideoEntity cameraEntity) {
    if (groupTracker.onlineCameraMap.containsKey(cameraEntity.getEntityID()) && cameraEntity.getStatus() == Status.ONLINE) {
      OnvifCameraService onvifCameraHandler = groupTracker.onlineCameraMap.get(cameraEntity.getEntityID());
      if (!cameraOrder.contains(onvifCameraHandler)) {
        log.info("Adding {} to a camera group.", cameraEntity.getTitle());
        if (hlsTurnedOn) {
          log.info("Starting HLS for the new camera.");
          onvifCameraHandler.startStream(true);
        }
        cameraOrder.add(onvifCameraHandler);
      }
    }
  }

  // Event based. This is called as each camera comes online after the group handler is registered.
  public void cameraOffline(OnvifCameraService handle) {
    if (cameraOrder.remove(handle)) {
      log.info("[{}]: Camera {} went offline and was removed from a group.", handle.getEntityID(), handle.getEntity());
    }
  }

  boolean addIfOnline(String UniqueID) {
        /*TODO: if (groupTracker.listOfOnlineCameraUID.contains(UniqueID)) {
            addCamera(UniqueID);
            return true;
        }*/
    return false;
  }

  void createCameraOrder() {
        /*TODO: addIfOnline(groupConfig.getFirstCamera());
        addIfOnline(groupConfig.getSecondCamera());
        if (!groupConfig.getThirdCamera().isEmpty()) {
            addIfOnline(groupConfig.getThirdCamera());
        }
        if (!groupConfig.getForthCamera().isEmpty()) {
            addIfOnline(groupConfig.getForthCamera());
        }
        // Cameras can now send events of when they go on and offline.
        groupTracker.listOfGroupHandlers.add(this);*/
  }

  int checkForMotion(int nextCamerasIndex) {
    int checked = 0;
    for (int index = nextCamerasIndex; checked < cameraOrder.size(); checked++) {
      if (cameraOrder.get(index).isMotionDetected()) {
        return index;
      }
      if (++index >= cameraOrder.size()) {
        index = 0;
      }
    }
    return nextCamerasIndex;
  }

  void pollCameraGroup() {
    if (cameraOrder.isEmpty()) {
      createCameraOrder();
    }
    if (++cameraIndex >= cameraOrder.size()) {
      cameraIndex = 0;
    }
    if (motionChangesOrder) {
      cameraIndex = checkForMotion(cameraIndex);
    }
    if (hlsTurnedOn) {
      discontinuitySequence++;
      createPlayList();
    }
  }

  public void start(OnOffType command) {
    if (OnOffType.ON.equals(command)) {
      hlsTurnedOn = true;
      for (OnvifCameraService handler : cameraOrder) {
        handler.startStream(true);
      }
    } else {
      // Do we turn all controls OFF, or do we remember the state before we turned them all on?
      hlsTurnedOn = false;
    }
  }

  // @Override
  public void initialize() {
        /*TODO: serverPort = groupConfig.getServerPort();
        pollTimeInSeconds = new BigDecimal(groupConfig.getPollTime());
        pollTimeInSeconds = pollTimeInSeconds.divide(new BigDecimal(1000), 1, RoundingMode.HALF_UP);
        motionChangesOrder = groupConfig.getMotionChangesOrder();

        if (serverPort == -1) {
            log.warn("The serverPort = -1 which disables a lot of features. See readme for more info.");
        } else if (serverPort < 1025) {
            log.warn("The serverPort is <= 1024 and may cause permission errors under Linux, try a higher port.");
        }
        if (groupConfig.getServerPort() > 0) {
            startStreamServer(true);
        }
        updateStatus(Status.ONLINE, null);
        pollCameraGroupJob = pollCameraGroup.scheduleWithFixedDelay(this::pollCameraGroup, 10000,
                groupConfig.getPollTime(), TimeUnit.MILLISECONDS);*/
  }

  //@Override
  public void dispose() {
    startStreamServer(false);
    groupTracker.listOfGroupHandlers.remove(this);
    Future<?> future = pollCameraGroupJob;
    if (future != null) {
      future.cancel(true);
    }
    cameraOrder.clear();
  }
}

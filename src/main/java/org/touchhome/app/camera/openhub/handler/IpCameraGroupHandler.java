package org.touchhome.app.camera.openhub.handler;

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
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.camera.entity.BaseCameraEntity;
import org.touchhome.app.camera.entity.WidgetCameraEntity;
import org.touchhome.app.camera.openhub.GroupTracker;
import org.touchhome.app.camera.openhub.StreamServerGroupHandler;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.State;
import org.touchhome.bundle.api.measure.StringType;
import org.touchhome.bundle.api.model.Status;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for finding cameras that are part of this group and displaying a group picture.
 */
@Log4j2
public class IpCameraGroupHandler {

    private BigDecimal pollTimeInSeconds = new BigDecimal(2);
    public ArrayList<IpCameraHandler> cameraOrder = new ArrayList<>(2);
    private EventLoopGroup serversLoopGroup = new NioEventLoopGroup();
    private final ScheduledExecutorService pollCameraGroup = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pollCameraGroupJob = null;
    private ServerBootstrap serverBootstrap;
    private ChannelFuture serverFuture = null;
    private WidgetCameraEntity widgetCameraEntity;
    public String hostIp;
    private boolean motionChangesOrder = true;
    public int serverPort = 0;
    public String playList = "";
    private String playingNow = "";
    public int cameraIndex = 0;
    public boolean hlsTurnedOn = false;
    private int entries = 0;
    private int mediaSequence = 1;
    private int discontinuitySequence = 0;
    private GroupTracker groupTracker;

    public IpCameraGroupHandler(WidgetCameraEntity widgetCameraEntity, String hostIp, GroupTracker groupTracker) {
        this.widgetCameraEntity = widgetCameraEntity;
        this.hostIp = hostIp;
        this.groupTracker = groupTracker;
    }

    public String getPlayList() {
        return playList;
    }

    public String getOutputFolder(int index) {
        IpCameraHandler handle = cameraOrder.get(index);
        return handle.getFfmpegOutputPath().toString();
    }

    private String readCamerasPlaylist(int cameraIndex) {
        String camerasm3u8 = "";
        IpCameraHandler handle = cameraOrder.get(cameraIndex);
        try {
            Path file = handle.getFfmpegOutputPath().resolve("ipcamera.m3u8");
            camerasm3u8 = new String(Files.readAllBytes(file));
        } catch (IOException e) {
            log.warn("Error occured fetching a groupDisplay cameras m3u8 file :{}", e.getMessage());
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
        m3u8File = m3u8File.replace("ipcamera", cameraIndex + "ipcamera"); // add index so we can then fetch output path
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
                    updateState(CHANNEL_MJPEG_URL,
                            new StringType("http://" + hostIp + ":" + serverPort + "/ipcamera.mjpeg"));
                    updateState(CHANNEL_HLS_URL,
                            new StringType("http://" + hostIp + ":" + serverPort + "/ipcamera.m3u8"));
                    updateState(CHANNEL_IMAGE_URL,
                            new StringType("http://" + hostIp + ":" + serverPort + "/ipcamera.jpg"));
                } catch (Exception e) {
                    updateStatus(Status.OFFLINE, "Exception occured when starting the streaming server. Try changing the serverPort to another number.");
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

    void addCamera(BaseCameraEntity cameraEntity) {
        if (groupTracker.onlineCameraMap.containsKey(cameraEntity.getEntityID()) && cameraEntity.getStatus() == Status.ONLINE) {
            IpCameraHandler ipCameraHandler = groupTracker.onlineCameraMap.get(cameraEntity.getEntityID());
            if (!cameraOrder.contains(ipCameraHandler)) {
                log.info("Adding {} to a camera group.", cameraEntity.getTitle());
                if (hlsTurnedOn) {
                    log.info("Starting HLS for the new camera.");
                    ipCameraHandler.start(OnOffType.ON);
                }
                cameraOrder.add(ipCameraHandler);
            }
        }
    }

    // Event based. This is called as each camera comes online after the group handler is registered.
    public void cameraOnline(BaseCameraEntity cameraEntity) {
        log.debug("New camera {} came online, checking if part of this group", cameraEntity.getEntityID());
        widgetCameraEntity.getSeries().stream().filter(s -> s.getDataSource().equals(cameraEntity.getEntityID()))
                .findAny().ifPresent(widgetCameraSeriesEntity -> {
            addCamera(cameraEntity);
        });
    }

    // Event based. This is called as each camera comes online after the group handler is registered.
    public void cameraOffline(IpCameraHandler handle) {
        if (cameraOrder.remove(handle)) {
            log.info("Camera {} went offline and was removed from a group.", handle.getCameraEntity().getTitle());
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
            if (cameraOrder.get(index).motionDetected) {
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
            for (IpCameraHandler handler : cameraOrder) {
                handler.start(OnOffType.ON);
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
        pollCameraGroupJob = pollCameraGroup.scheduleAtFixedRate(this::pollCameraGroup, 10000,
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

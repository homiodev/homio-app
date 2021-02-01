package org.touchhome.app.camera.openhub.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.touchhome.app.camera.entity.CameraType;
import org.touchhome.app.camera.entity.IpCameraEntity;
import org.touchhome.app.camera.openhub.*;
import org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;
import org.touchhome.app.camera.openhub.onvif.OnvifConnection;
import org.touchhome.app.camera.openhub.type.*;
import org.touchhome.app.camera.setting.CameraFFMPEGInstallPathOptions;
import org.touchhome.app.camera.setting.CameraFFMPEGOutputSetting;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.measure.*;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
public class IpCameraHandler {

    private ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(4);
    private GroupTracker groupTracker;

    // ChannelGroup is thread safe
    public final ChannelGroup mjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup snapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup autoSnapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public final ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public Ffmpeg ffmpegHLS = null;
    public Ffmpeg ffmpegRecord = null;
    public Ffmpeg ffmpegGIF = null;
    public Ffmpeg ffmpegRtspHelper = null;
    public Ffmpeg ffmpegMjpeg = null;
    public Ffmpeg ffmpegSnapshot = null;
    public boolean streamingAutoFps = false;
    public boolean motionDetected = false;

    private ScheduledFuture<?> cameraConnectionJob = null;
    private ScheduledFuture<?> pollCameraJob = null;
    private ScheduledFuture<?> snapshotJob = null;
    private Bootstrap mainBootstrap;
    private ServerBootstrap serverBootstrap;

    private EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();
    private EventLoopGroup serversLoopGroup = new NioEventLoopGroup();
    private FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"),
            "");
    private String gifFilename = "ipcamera";
    private String gifHistory = "";
    private String mp4History = "";
    public int gifHistoryLength;
    public int mp4HistoryLength;
    private String mp4Filename = "ipcamera";
    private int mp4RecordTime;
    private int gifRecordTime = 5;
    private LinkedList<byte[]> fifoSnapshotBuffer = new LinkedList<>();
    private int snapCount;
    private boolean updateImageChannel = false;
    private boolean updateAutoFps = false;
    private byte lowPriorityCounter = 0;
    public String hostIp;
    public Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();
    public List<String> lowPriorityRequests = new ArrayList<>(0);

    // basicAuth MUST remain private as it holds the cameraEntity.getPassword()
    private String basicAuth = "";
    public boolean useBasicAuth = false;
    public boolean useDigestAuth = false;
    public String snapshotUri = "";
    public String mjpegUri = "";
    private ChannelFuture serverFuture = null;
    private Object firstStreamedMsg = new Object();
    public byte[] currentSnapshot = new byte[]{(byte) 0x00};
    public ReentrantLock lockCurrentSnapshot = new ReentrantLock();
    public String rtspUri = "";
    public boolean audioAlarmUpdateSnapshot = false;
    private boolean motionAlarmUpdateSnapshot = false;
    private boolean isOnline = false; // Used so only 1 error is logged when a network issue occurs.
    private boolean firstAudioAlarm = false;
    private boolean firstMotionAlarm = false;
    public Double motionThreshold = 0.0016;
    public int audioThreshold = 35;

    private boolean streamingSnapshotMjpeg = false;
    public boolean motionAlarmEnabled = false;
    public boolean audioAlarmEnabled = false;
    public boolean ffmpegSnapshotGeneration = false;
    public boolean snapshotPolling = false;
    public OnvifConnection onvifCamera = new OnvifConnection(this, "", "", "");
    @Getter
    private IpCameraEntity cameraEntity;
    private EntityContext entityContext;
    @Getter
    private Path ffmpegOutputPath;
    private String ffmpegLocation;

    // These methods handle the response from all camera brands, nothing specific to 1 brand.
    private class CommonCameraHandler extends ChannelDuplexHandler {
        private int bytesToRecieve = 0;
        private int bytesAlreadyRecieved = 0;
        private byte[] incomingJpeg = new byte[0];
        private String incomingMessage = "";
        private String contentType = "empty";
        private Object reply = new Object();
        private String requestUrl = "";
        private boolean closeConnection = true;
        private boolean isChunked = false;

        public void setURL(String url) {
            requestUrl = url;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg == null || ctx == null) {
                return;
            }
            try {
                if (msg instanceof HttpResponse) {
                    HttpResponse response = (HttpResponse) msg;
                    if (response.status().code() != 401) {
                        if (!response.headers().isEmpty()) {
                            for (String name : response.headers().names()) {
                                // Some cameras use first letter uppercase and others dont.
                                switch (name.toLowerCase()) { // Possible localization issues doing this
                                    case "content-type":
                                        contentType = response.headers().getAsString(name);
                                        break;
                                    case "content-length":
                                        bytesToRecieve = Integer.parseInt(response.headers().getAsString(name));
                                        break;
                                    case "connection":
                                        if (response.headers().getAsString(name).contains("keep-alive")) {
                                            closeConnection = false;
                                        }
                                        break;
                                    case "transfer-encoding":
                                        if (response.headers().getAsString(name).contains("chunked")) {
                                            isChunked = true;
                                        }
                                        break;
                                }
                            }
                            if (contentType.contains("multipart")) {
                                closeConnection = false;
                                if (mjpegUri.equals(requestUrl)) {
                                    if (msg instanceof HttpMessage) {
                                        // very start of stream only
                                        ReferenceCountUtil.retain(msg, 1);
                                        firstStreamedMsg = msg;
                                        streamToGroup(firstStreamedMsg, mjpegChannelGroup, true);
                                    }
                                }
                            } else if (contentType.contains("image/jp")) {
                                if (bytesToRecieve == 0) {
                                    bytesToRecieve = 768000; // 0.768 Mbyte when no Content-Length is sent
                                    log.debug("Camera has no Content-Length header, we have to guess how much RAM.");
                                }
                                incomingJpeg = new byte[bytesToRecieve];
                            }
                        }
                    }
                }
                if (msg instanceof HttpContent) {
                    if (mjpegUri.equals(requestUrl)) {
                        // multiple MJPEG stream packets come back as this.
                        ReferenceCountUtil.retain(msg, 1);
                        streamToGroup(msg, mjpegChannelGroup, true);
                    } else {
                        HttpContent content = (HttpContent) msg;
                        // Found some cameras use Content-Type: image/jpg instead of image/jpeg
                        if (contentType.contains("image/jp")) {
                            for (int i = 0; i < content.content().capacity(); i++) {
                                incomingJpeg[bytesAlreadyRecieved++] = content.content().getByte(i);
                            }
                            if (content instanceof LastHttpContent) {
                                processSnapshot(incomingJpeg);
                                // testing next line and if works need to do a full cleanup of this function.
                                closeConnection = true;
                                if (closeConnection) {
                                    ctx.close();
                                } else {
                                    bytesToRecieve = 0;
                                    bytesAlreadyRecieved = 0;
                                }
                            }
                        } else { // incomingMessage that is not an IMAGE
                            if (incomingMessage.isEmpty()) {
                                incomingMessage = content.content().toString(CharsetUtil.UTF_8);
                            } else {
                                incomingMessage += content.content().toString(CharsetUtil.UTF_8);
                            }
                            bytesAlreadyRecieved = incomingMessage.length();
                            if (content instanceof LastHttpContent) {
                                // If it is not an image send it on to the next handler//
                                if (bytesAlreadyRecieved != 0) {
                                    reply = incomingMessage;
                                    super.channelRead(ctx, reply);
                                }
                            }
                            // Alarm Streams never have a LastHttpContent as they always stay open//
                            else if (contentType.contains("multipart")) {
                                if (bytesAlreadyRecieved != 0) {
                                    reply = incomingMessage;
                                    incomingMessage = "";
                                    bytesToRecieve = 0;
                                    bytesAlreadyRecieved = 0;
                                    super.channelRead(ctx, reply);
                                }
                            }
                            // Foscam needs this as will other cameras with chunks//
                            if (isChunked && bytesAlreadyRecieved != 0) {
                                log.debug("Reply is chunked.");
                                reply = incomingMessage;
                                super.channelRead(ctx, reply);
                            }
                        }
                    }
                } else { // msg is not HttpContent
                    // Foscam cameras need this
                    if (!contentType.contains("image/jp") && bytesAlreadyRecieved != 0) {
                        reply = incomingMessage;
                        log.debug("Packet back from camera is {}", incomingMessage);
                        super.channelRead(ctx, reply);
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause == null || ctx == null) {
                return;
            }
            if (cause instanceof ArrayIndexOutOfBoundsException) {
                log.debug("Camera sent {} bytes when the content-length header was {}.", bytesAlreadyRecieved,
                        bytesToRecieve);
            } else {
                log.warn("!!!! Camera possibly closed the channel on the binding, cause reported is: {}",
                        cause.getMessage());
            }
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (ctx == null) {
                return;
            }
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                // If camera does not use the channel for X amount of time it will close.
                if (e.state() == IdleState.READER_IDLE) {
                    String urlToKeepOpen = "";
                    switch (cameraEntity.getCameraType()) {
                        case dahua:
                            urlToKeepOpen = "/cgi-bin/eventManager.cgi?action=attach&codes=[All]";
                            break;
                        case hikvision:
                            urlToKeepOpen = "/ISAPI/Event/notification/alertStream";
                            break;
                        case doorbird:
                            urlToKeepOpen = "/bha-api/monitor.cgi?ring=doorbell,motionsensor";
                            break;
                    }
                    ChannelTracking channelTracking = channelTrackingMap.get(urlToKeepOpen);
                    if (channelTracking != null) {
                        if (channelTracking.getChannel() == ctx.channel()) {
                            return; // don't auto close this as it is for the alarms.
                        }
                    }
                    ctx.close();
                }
            }
        }
    }

    public IpCameraHandler(IpCameraEntity cameraEntity, EntityContext entityContext,
                           String ipAddress, GroupTracker groupTracker) {
        this.cameraEntity = cameraEntity;
        this.entityContext = entityContext;
        this.hostIp = ipAddress;
        this.groupTracker = groupTracker;

        entityContext.setting().listenValueAndGet(CameraFFMPEGOutputSetting.class, "listen-ffmpeg-output-path-" + cameraEntity.getEntityID(),
                path -> ffmpegOutputPath = path);

        // for custom ffmpeg path
        entityContext.setting().listenValueAndGet(CameraFFMPEGInstallPathOptions.class, "listen-ffmpeg-path-" + cameraEntity.getEntityID(),
                path -> this.ffmpegLocation = path == null ? "" : path.toString());
    }

    // false clears the stored user/pass hash, true creates the hash
    public boolean setBasicAuth(boolean useBasic) {
        if (!useBasic) {
            log.debug("Clearing out the stored BASIC auth now.");
            basicAuth = "";
            return false;
        } else if (!basicAuth.isEmpty()) {
            // due to camera may have been sent multiple requests before the auth was set, this may trigger falsely.
            log.warn("Camera is reporting your username and/or password is wrong.");
            return false;
        }
        if (!isEmpty(cameraEntity.getUser()) && !isEmpty(cameraEntity.getPassword())) {
            String authString = cameraEntity.getUser() + ":" + cameraEntity.getPassword();
            ByteBuf byteBuf = null;
            try {
                byteBuf = Base64.encode(Unpooled.wrappedBuffer(authString.getBytes(CharsetUtil.UTF_8)));
                basicAuth = byteBuf.getCharSequence(0, byteBuf.capacity(), CharsetUtil.UTF_8).toString();
            } finally {
                if (byteBuf != null) {
                    byteBuf.release();
                }
            }
            return true;
        } else {
            cameraConfigError("Camera is asking for Basic Auth when you have not provided a username and/or password.");
        }
        return false;
    }

    private String getCorrectUrlFormat(String longUrl) {
        String temp = longUrl;
        URL url;

        if (longUrl.isEmpty() || longUrl.equals("ffmpeg")) {
            return longUrl;
        }

        try {
            url = new URL(longUrl);
            int port = url.getPort();
            if (port == -1) {
                if (url.getQuery() == null) {
                    temp = url.getPath();
                } else {
                    temp = url.getPath() + "?" + url.getQuery();
                }
            } else {
                if (url.getQuery() == null) {
                    temp = ":" + url.getPort() + url.getPath();
                } else {
                    temp = ":" + url.getPort() + url.getPath() + "?" + url.getQuery();
                }
            }
        } catch (MalformedURLException e) {
            cameraConfigError("A non valid URL has been given to the binding, check they work in a browser.");
        }
        return temp;
    }

    public void sendHttpPUT(String httpRequestURL, FullHttpRequest request) {
        putRequestWithBody = request; // use Global so the authhandler can use it when resent with DIGEST.
        sendHttpRequest("PUT", httpRequestURL, null);
    }

    public void sendHttpGET(String httpRequestURL) {
        sendHttpRequest("GET", httpRequestURL, null);
    }

    public int getPortFromShortenedUrl(String httpRequestURL) {
        if (httpRequestURL.startsWith(":")) {
            int end = httpRequestURL.indexOf("/");
            return Integer.parseInt(httpRequestURL.substring(1, end));
        }
        return cameraEntity.getPort();
    }

    public String getTinyUrl(String httpRequestURL) {
        if (httpRequestURL.startsWith(":")) {
            int beginIndex = httpRequestURL.indexOf("/");
            return httpRequestURL.substring(beginIndex);
        }
        return httpRequestURL;
    }

    // Always use this as sendHttpGET(GET/POST/PUT/DELETE, "/foo/bar",null)//
    // The authHandler will generate a digest string and re-send using this same function when needed.
    @SuppressWarnings("null")
    public void sendHttpRequest(String httpMethod, String httpRequestURLFull, String digestString) {
        int port = getPortFromShortenedUrl(httpRequestURLFull);
        String httpRequestURL = getTinyUrl(httpRequestURLFull);

        if (mainBootstrap == null) {
            mainBootstrap = new Bootstrap();
            mainBootstrap.group(mainEventLoopGroup);
            mainBootstrap.channel(NioSocketChannel.class);
            mainBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            mainBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4500);
            mainBootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 8);
            mainBootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
            mainBootstrap.option(ChannelOption.TCP_NODELAY, true);
            mainBootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @Override
                public void initChannel(SocketChannel socketChannel) {
                    // HIK Alarm stream needs > 9sec idle to stop stream closing
                    socketChannel.pipeline().addLast(new IdleStateHandler(18, 0, 0));
                    socketChannel.pipeline().addLast(new HttpClientCodec());
                    socketChannel.pipeline().addLast(AUTH_HANDLER,
                            new MyNettyAuthHandler(cameraEntity.getUser(), cameraEntity.getPassword(), IpCameraHandler.this));
                    socketChannel.pipeline().addLast(COMMON_HANDLER, new CommonCameraHandler());

                    switch (cameraEntity.getCameraType()) {
                        case amcrest:
                            socketChannel.pipeline().addLast(AMCREST_HANDLER, new AmcrestHandler(IpCameraHandler.this));
                            break;
                        case dahua:
                            socketChannel.pipeline()
                                    .addLast(new DahuaHandler(IpCameraHandler.this, cameraEntity.getNvrChannel()));
                            break;
                        case doorbird:
                            socketChannel.pipeline().addLast(new DoorBirdHandler(IpCameraHandler.this));
                            break;
                        case foscam:
                            socketChannel.pipeline().addLast(
                                    new FoscamHandler(IpCameraHandler.this, cameraEntity.getUser(), cameraEntity.getPassword()));
                            break;
                        case hikvision:
                            socketChannel.pipeline()
                                    .addLast(new HikvisionHandler(IpCameraHandler.this, cameraEntity.getNvrChannel()));
                            break;
                        case instar:
                            socketChannel.pipeline().addLast(INSTAR_HANDLER, new InstarHandler(IpCameraHandler.this));
                            break;
                        default:
                            socketChannel.pipeline().addLast(new HttpOnlyHandler(IpCameraHandler.this));
                            break;
                    }
                }
            });
        }

        FullHttpRequest request;
        if (!"PUT".equals(httpMethod) || (useDigestAuth && digestString == null)) {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod(httpMethod), httpRequestURL);
            request.headers().set("Host", cameraEntity.getIp() + ":" + port);
            request.headers().set("Connection", HttpHeaderValues.KEEP_ALIVE);
        } else {
            request = putRequestWithBody;
        }

        if (!basicAuth.isEmpty()) {
            if (useDigestAuth) {
                log.warn("Camera at IP:{} had both Basic and Digest set to be used", cameraEntity.getIp());
                setBasicAuth(false);
            } else {
                request.headers().set("Authorization", "Basic " + basicAuth);
            }
        }

        if (useDigestAuth) {
            if (digestString != null) {
                request.headers().set("Authorization", "Digest " + digestString);
            }
        }

        mainBootstrap.connect(new InetSocketAddress(cameraEntity.getIp(), port))
                .addListener((ChannelFutureListener) future -> {
                    if (future == null) {
                        return;
                    }
                    if (future.isDone() && future.isSuccess()) {
                        Channel ch = future.channel();
                        openChannels.add(ch);
                        if (!isOnline) {
                            bringCameraOnline();
                        }
                        log.trace("Sending camera: {}: http://{}:{}{}", httpMethod, cameraEntity.getIp(), port,
                                httpRequestURL);
                        channelTrackingMap.put(httpRequestURL, new ChannelTracking(ch, httpRequestURL));

                        CommonCameraHandler commonHandler = (CommonCameraHandler) ch.pipeline().get(COMMON_HANDLER);
                        commonHandler.setURL(httpRequestURLFull);
                        MyNettyAuthHandler authHandler = (MyNettyAuthHandler) ch.pipeline().get(AUTH_HANDLER);
                        authHandler.setURL(httpMethod, httpRequestURL);

                        switch (cameraEntity.getCameraType()) {
                            case amcrest:
                                AmcrestHandler amcrestHandler = (AmcrestHandler) ch.pipeline().get(AMCREST_HANDLER);
                                amcrestHandler.setURL(httpRequestURL);
                                break;
                            case instar:
                                InstarHandler instarHandler = (InstarHandler) ch.pipeline().get(INSTAR_HANDLER);
                                instarHandler.setURL(httpRequestURL);
                                break;
                        }
                        ch.writeAndFlush(request);
                    } else { // an error occured
                        cameraCommunicationError(
                                "Connection Timeout: Check your IP and PORT are correct and the camera can be reached.");
                    }
                });
    }

    public void processSnapshot(byte[] incommingSnapshot) {
        lockCurrentSnapshot.lock();
        try {
            currentSnapshot = incommingSnapshot;
            if (cameraEntity.getGifPreroll() > 0) {
                fifoSnapshotBuffer.add(incommingSnapshot);
                if (fifoSnapshotBuffer.size() > (cameraEntity.getGifPreroll() + gifRecordTime)) {
                    fifoSnapshotBuffer.removeFirst();
                }
            }
        } finally {
            lockCurrentSnapshot.unlock();
        }

        if (streamingSnapshotMjpeg) {
            sendMjpegFrame(incommingSnapshot, snapshotMjpegChannelGroup);
        }
        if (streamingAutoFps) {
            if (motionDetected) {
                sendMjpegFrame(incommingSnapshot, autoSnapshotMjpegChannelGroup);
            } else if (updateAutoFps) {
                // only happens every 8 seconds as some browsers need a frame that often to keep stream alive.
                sendMjpegFrame(incommingSnapshot, autoSnapshotMjpegChannelGroup);
                updateAutoFps = false;
            }
        }

        if (updateImageChannel) {
            updateState(CHANNEL_IMAGE, new RawType(incommingSnapshot, "image/jpeg"));
        } else if (firstMotionAlarm || motionAlarmUpdateSnapshot) {
            updateState(CHANNEL_IMAGE, new RawType(incommingSnapshot, "image/jpeg"));
            firstMotionAlarm = motionAlarmUpdateSnapshot = false;
        } else if (firstAudioAlarm || audioAlarmUpdateSnapshot) {
            updateState(CHANNEL_IMAGE, new RawType(incommingSnapshot, "image/jpeg"));
            firstAudioAlarm = audioAlarmUpdateSnapshot = false;
        }
    }

    private Map<String, Object> channelStates = new HashMap<>();

    private void updateState(String channelName, State state) {
        channelStates.put(channelName, state);
    }

    public void stopStreamServer() {
        serversLoopGroup.shutdownGracefully();
        serverBootstrap = null;
    }

    public void startStreamServer() {
        if (serverBootstrap == null) {
            try {
                serversLoopGroup = new NioEventLoopGroup();
                serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(serversLoopGroup);
                serverBootstrap.channel(NioServerSocketChannel.class);
                // IP "0.0.0.0" will bind the server to all network connections//
                serverBootstrap.localAddress(new InetSocketAddress("0.0.0.0", cameraEntity.getServerPort()));
                serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 60, 0));
                        socketChannel.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
                        socketChannel.pipeline().addLast("ChunkedWriteHandler", new ChunkedWriteHandler());
                        socketChannel.pipeline().addLast("streamServerHandler", new StreamServerHandler(IpCameraHandler.this));
                    }
                });
                serverFuture = serverBootstrap.bind().sync();
                serverFuture.await(4000);
                log.debug("File server for camera at {} has started on port {} for all NIC's.", cameraEntity.getIp(),
                        cameraEntity.getServerPort());
                updateState(CHANNEL_MJPEG_URL,
                        new StringType("http://" + hostIp + ":" + cameraEntity.getServerPort() + "/ipcamera.mjpeg"));
                updateState(CHANNEL_HLS_URL,
                        new StringType("http://" + hostIp + ":" + cameraEntity.getServerPort() + "/ipcamera.m3u8"));
                updateState(CHANNEL_IMAGE_URL,
                        new StringType("http://" + hostIp + ":" + cameraEntity.getServerPort() + "/ipcamera.jpg"));
            } catch (Exception e) {
                cameraConfigError("Exception when starting server. Try changing the Server Port to another number.");
            }
            if (cameraEntity.getCameraType() == CameraType.instar) {
                log.debug("Setting up the Alarm Server settings in the camera now");
                sendHttpGET(
                        "/param.cgi?cmd=setmdalarm&-aname=server2&-switch=on&-interval=1&cmd=setalarmserverattr&-as_index=3&-as_server="
                                + hostIp + "&-as_port=" + cameraEntity.getServerPort()
                                + "&-as_path=/instar&-as_queryattr1=&-as_queryval1=&-as_queryattr2=&-as_queryval2=&-as_queryattr3=&-as_queryval3=&-as_activequery=1&-as_auth=0&-as_query1=0&-as_query2=0&-as_query3=0");
            }
        }
    }

    public void setupSnapshotStreaming(boolean stream, ChannelHandlerContext ctx, boolean auto) {
        if (stream) {
            sendMjpegFirstPacket(ctx);
            if (auto) {
                autoSnapshotMjpegChannelGroup.add(ctx.channel());
                lockCurrentSnapshot.lock();
                try {
                    sendMjpegFrame(currentSnapshot, autoSnapshotMjpegChannelGroup);
                    // iOS uses a FIFO? and needs two frames to display a pic
                    sendMjpegFrame(currentSnapshot, autoSnapshotMjpegChannelGroup);
                } finally {
                    lockCurrentSnapshot.unlock();
                }
                streamingAutoFps = true;
            } else {
                snapshotMjpegChannelGroup.add(ctx.channel());
                lockCurrentSnapshot.lock();
                try {
                    sendMjpegFrame(currentSnapshot, snapshotMjpegChannelGroup);
                } finally {
                    lockCurrentSnapshot.unlock();
                }
                streamingSnapshotMjpeg = true;
                startSnapshotPolling();
            }
        } else {
            snapshotMjpegChannelGroup.remove(ctx.channel());
            autoSnapshotMjpegChannelGroup.remove(ctx.channel());
            if (streamingSnapshotMjpeg && snapshotMjpegChannelGroup.isEmpty()) {
                streamingSnapshotMjpeg = false;
                stopSnapshotPolling();
                log.debug("All snapshots.mjpeg streams have stopped.");
            } else if (streamingAutoFps && autoSnapshotMjpegChannelGroup.isEmpty()) {
                streamingAutoFps = false;
                stopSnapshotPolling();
                log.debug("All autofps.mjpeg streams have stopped.");
            }
        }
    }

    // If start is true the CTX is added to the list to stream video to, false stops
    // the stream.
    public void setupMjpegStreaming(boolean start, ChannelHandlerContext ctx) {
        if (start) {
            if (mjpegChannelGroup.isEmpty()) {// first stream being requested.
                mjpegChannelGroup.add(ctx.channel());
                if (mjpegUri.isEmpty() || mjpegUri.equals("ffmpeg")) {
                    sendMjpegFirstPacket(ctx);
                    setupFfmpegFormat(FFmpegFormat.MJPEG);
                } else {
                    try {
                        // fix Dahua reboots when refreshing a mjpeg stream.
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    sendHttpGET(mjpegUri);
                }
            } else if (ffmpegMjpeg != null) {// not first stream and we will use ffmpeg
                sendMjpegFirstPacket(ctx);
                mjpegChannelGroup.add(ctx.channel());
            } else {// not first stream and camera supplies the mjpeg source.
                ctx.channel().writeAndFlush(firstStreamedMsg);
                mjpegChannelGroup.add(ctx.channel());
            }
        } else {
            mjpegChannelGroup.remove(ctx.channel());
            if (mjpegChannelGroup.isEmpty()) {
                log.debug("All ipcamera.mjpeg streams have stopped.");
                if (mjpegUri.equals("ffmpeg") || mjpegUri.isEmpty()) {
                    Ffmpeg localMjpeg = ffmpegMjpeg;
                    if (localMjpeg != null) {
                        localMjpeg.stopConverting();
                    }
                } else {
                    closeChannel(getTinyUrl(mjpegUri));
                }
            }
        }
    }

    void closeChannel(String url) {
        ChannelTracking channelTracking = channelTrackingMap.get(url);
        if (channelTracking != null) {
            if (channelTracking.getChannel().isOpen()) {
                channelTracking.getChannel().close();
            }
        }
    }

    /**
     * This method should never run under normal use, if there is a bug in a camera or binding it may be possible to
     * open large amounts of channels. This may help to keep it under control and WARN the user every 8 seconds this is
     * still occurring.
     */
    void cleanChannels() {
        for (Channel channel : openChannels) {
            boolean oldChannel = true;
            for (ChannelTracking channelTracking : channelTrackingMap.values()) {
                if (!channelTracking.getChannel().isOpen() && channelTracking.getReply().isEmpty()) {
                    channelTrackingMap.remove(channelTracking.getRequestUrl());
                }
                if (channelTracking.getChannel() == channel) {
                    log.trace("Open channel to camera is used for URL:{}", channelTracking.getRequestUrl());
                    oldChannel = false;
                }
            }
            if (oldChannel) {
                channel.close();
            }
        }
    }

    public void storeHttpReply(String url, String content) {
        ChannelTracking channelTracking = channelTrackingMap.get(url);
        if (channelTracking != null) {
            channelTracking.setReply(content);
        }
    }

    // sends direct to ctx so can be either snapshots.mjpeg or normal mjpeg stream
    public void sendMjpegFirstPacket(ChannelHandlerContext ctx) {
        final String boundary = "thisMjpegStream";
        String contentType = "multipart/x-mixed-replace; boundary=" + boundary;
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().add("Access-Control-Allow-Origin", "*");
        response.headers().add("Access-Control-Expose-Headers", "*");
        ctx.channel().writeAndFlush(response);
    }

    public void sendMjpegFrame(byte[] jpg, ChannelGroup channelGroup) {
        final String boundary = "thisMjpegStream";
        ByteBuf imageByteBuf = Unpooled.copiedBuffer(jpg);
        int length = imageByteBuf.readableBytes();
        String header = "--" + boundary + "\r\n" + "content-type: image/jpeg" + "\r\n" + "content-length: " + length
                + "\r\n\r\n";
        ByteBuf headerBbuf = Unpooled.copiedBuffer(header, 0, header.length(), StandardCharsets.UTF_8);
        ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
        streamToGroup(headerBbuf, channelGroup, false);
        streamToGroup(imageByteBuf, channelGroup, false);
        streamToGroup(footerBbuf, channelGroup, true);
    }

    public void streamToGroup(Object msg, ChannelGroup channelGroup, boolean flush) {
        channelGroup.write(msg);
        if (flush) {
            channelGroup.flush();
        }
    }

    private void storeSnapshots() {
        int count = 0;
        // Need to lock as fifoSnapshotBuffer is not thread safe and new snapshots can be incoming.
        lockCurrentSnapshot.lock();
        try {
            for (byte[] foo : fifoSnapshotBuffer) {
                Path file = ffmpegOutputPath.resolve("snapshot" + count + ".jpg");
                count++;
                try {
                    Files.write(file, foo);
                } catch (FileNotFoundException e) {
                    log.warn("FileNotFoundException {}", e.getMessage());
                } catch (IOException e) {
                    log.warn("IOException {}", e.getMessage());
                }
            }
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    @SneakyThrows
    public void setupFfmpegFormat(FFmpegFormat format) {
        String inputOptions = cameraEntity.getFfmpegInputOptions();
        if (rtspUri.isEmpty()) {
            log.warn("The camera tried to use a FFmpeg feature when no valid input for FFmpeg is provided.");
            return;
        }
        if (rtspUri.toLowerCase().contains("rtsp")) {
            if (inputOptions.isEmpty()) {
                inputOptions = "-rtsp_transport tcp";
            }
        }

        // Make sure the folder exists, if not create it.
        Files.createDirectories(ffmpegOutputPath);
        switch (format) {
            case HLS:
                if (ffmpegHLS == null) {
                    if (!inputOptions.isEmpty()) {
                        ffmpegHLS = new Ffmpeg(this, format, ffmpegLocation,
                                "-hide_banner -loglevel warning " + inputOptions, rtspUri,
                                cameraEntity.getHlsOutOptions(), ffmpegOutputPath.resolve("ipcamera.m3u8").toString(),
                                cameraEntity.getUser(), cameraEntity.getPassword());
                    } else {
                        ffmpegHLS = new Ffmpeg(this, format, ffmpegLocation,
                                "-hide_banner -loglevel warning", rtspUri, cameraEntity.getHlsOutOptions(),
                                ffmpegOutputPath.resolve("ipcamera.m3u8").toString(), cameraEntity.getUser(),
                                cameraEntity.getPassword());
                    }
                }
                Ffmpeg localHLS = ffmpegHLS;
                if (localHLS != null) {
                    localHLS.startConverting();
                }
                break;
            case GIF:
                if (cameraEntity.getGifPreroll() > 0) {
                    ffmpegGIF = new Ffmpeg(this, format, ffmpegLocation,
                            "-y -r 1 -hide_banner -loglevel warning", ffmpegOutputPath.resolve("snapshot%d.jpg").toString(),
                            "-frames:v " + (cameraEntity.getGifPreroll() + gifRecordTime) + " "
                                    + cameraEntity.getGifOutOptions(),
                            ffmpegOutputPath.resolve(gifFilename + ".gif").toString(), cameraEntity.getUser(),
                            cameraEntity.getPassword());
                } else {
                    if (!inputOptions.isEmpty()) {
                        inputOptions = "-y -t " + gifRecordTime + " -hide_banner -loglevel warning " + inputOptions;
                    } else {
                        inputOptions = "-y -t " + gifRecordTime + " -hide_banner -loglevel warning";
                    }
                    ffmpegGIF = new Ffmpeg(this, format, ffmpegLocation, inputOptions, rtspUri,
                            cameraEntity.getGifOutOptions(), ffmpegOutputPath.resolve(gifFilename + ".gif").toString(),
                            cameraEntity.getUser(), cameraEntity.getPassword());
                }
                if (cameraEntity.getGifPreroll() > 0) {
                    storeSnapshots();
                }
                Ffmpeg localGIF = ffmpegGIF;
                if (localGIF != null) {
                    localGIF.startConverting();
                    if (gifHistory.isEmpty()) {
                        gifHistory = gifFilename;
                    } else if (!gifFilename.equals("ipcamera")) {
                        gifHistory = gifFilename + "," + gifHistory;
                        if (gifHistoryLength > 49) {
                            int endIndex = gifHistory.lastIndexOf(",");
                            gifHistory = gifHistory.substring(0, endIndex);
                        }
                    }
                    setChannelState(CHANNEL_GIF_HISTORY, new StringType(gifHistory));
                }
                break;
            case RECORD:
                if (!inputOptions.isEmpty()) {
                    inputOptions = "-y -t " + mp4RecordTime + " -hide_banner -loglevel warning " + inputOptions;
                } else {
                    inputOptions = "-y -t " + mp4RecordTime + " -hide_banner -loglevel warning";
                }
                ffmpegRecord = new Ffmpeg(this, format, ffmpegLocation, inputOptions, rtspUri,
                        cameraEntity.getMp4OutOptions(), ffmpegOutputPath.resolve(mp4Filename + ".mp4").toString(),
                        cameraEntity.getUser(), cameraEntity.getPassword());
                Ffmpeg localRecord = ffmpegRecord;
                if (localRecord != null) {
                    localRecord.startConverting();
                    if (mp4History.isEmpty()) {
                        mp4History = mp4Filename;
                    } else if (!mp4Filename.equals("ipcamera")) {
                        mp4History = mp4Filename + "," + mp4History;
                        if (mp4HistoryLength > 49) {
                            int endIndex = mp4History.lastIndexOf(",");
                            mp4History = mp4History.substring(0, endIndex);
                        }
                    }
                }
                setChannelState(CHANNEL_MP4_HISTORY, new StringType(mp4History));
                break;
            case RTSP_ALARMS:
                Ffmpeg localAlarms = ffmpegRtspHelper;
                if (localAlarms != null) {
                    localAlarms.stopConverting();
                    if (!audioAlarmEnabled && !motionAlarmEnabled) {
                        return;
                    }
                }
                String input = (cameraEntity.getAlarmInputUrl().isEmpty()) ? rtspUri : cameraEntity.getAlarmInputUrl();
                String filterOptions;
                if (!audioAlarmEnabled) {
                    filterOptions = "-an";
                } else {
                    filterOptions = "-af silencedetect=n=-" + audioThreshold + "dB:d=2";
                }
                if (!motionAlarmEnabled && !ffmpegSnapshotGeneration) {
                    filterOptions = filterOptions.concat(" -vn");
                } else if (motionAlarmEnabled && !cameraEntity.getMotionOptions().isEmpty()) {
                    String usersMotionOptions = cameraEntity.getMotionOptions();
                    if (usersMotionOptions.startsWith("-")) {
                        // Need to put the users custom options first in the chain before the motion is detected
                        filterOptions += " " + usersMotionOptions + ",select='gte(scene," + motionThreshold
                                + ")',metadata=print";
                    } else {
                        filterOptions = filterOptions + " " + usersMotionOptions + " -vf select='gte(scene,"
                                + motionThreshold + ")',metadata=print";
                    }
                } else if (motionAlarmEnabled) {
                    filterOptions = filterOptions
                            .concat(" -vf select='gte(scene," + motionThreshold + ")',metadata=print");
                }
                ffmpegRtspHelper = new Ffmpeg(this, format, ffmpegLocation, inputOptions, input,
                        filterOptions, "-f null -", cameraEntity.getUser(), cameraEntity.getPassword());
                localAlarms = ffmpegRtspHelper;
                if (localAlarms != null) {
                    localAlarms.startConverting();
                }
                break;
            case MJPEG:
                if (ffmpegMjpeg == null) {
                    if (inputOptions.isEmpty()) {
                        inputOptions = "-hide_banner -loglevel warning";
                    } else {
                        inputOptions += " -hide_banner -loglevel warning";
                    }
                    ffmpegMjpeg = new Ffmpeg(this, format, ffmpegLocation, inputOptions, rtspUri,
                            cameraEntity.getMjpegOptions(),
                            "http://127.0.0.1:" + cameraEntity.getServerPort() + "/ipcamera.jpg",
                            cameraEntity.getUser(), cameraEntity.getPassword());
                }
                Ffmpeg localMjpeg = ffmpegMjpeg;
                if (localMjpeg != null) {
                    localMjpeg.startConverting();
                }
                break;
            case SNAPSHOT:
                // if mjpeg stream you can use 'ffmpeg -i input -codec:v copy -bsf:v mjpeg2jpeg output.jpg'
                if (ffmpegSnapshot == null) {
                    if (inputOptions.isEmpty()) {
                        // iFrames only
                        inputOptions = "-threads 1 -skip_frame nokey -hide_banner -loglevel warning";
                    } else {
                        inputOptions += " -threads 1 -skip_frame nokey -hide_banner -loglevel warning";
                    }
                    ffmpegSnapshot = new Ffmpeg(this, format, ffmpegLocation, inputOptions, rtspUri,
                            cameraEntity.getSnapshotOptions(),
                            "http://127.0.0.1:" + cameraEntity.getServerPort() + "/snapshot.jpg",
                            cameraEntity.getUser(), cameraEntity.getPassword());
                }
                Ffmpeg localSnaps = ffmpegSnapshot;
                if (localSnaps != null) {
                    localSnaps.startConverting();
                }
                break;
        }
    }

    public void noMotionDetected(String thisAlarmsChannel) {
        setChannelState(thisAlarmsChannel, OnOffType.OFF);
        firstMotionAlarm = false;
        motionAlarmUpdateSnapshot = false;
        motionDetected = false;
        if (streamingAutoFps) {
            stopSnapshotPolling();
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // During Motion Alarms
            stopSnapshotPolling();
        }
    }

    /**
     * The changeAlarmState To only be used to change alarms channels that are not counted as motion. This will
     * allow logic to be added here in the future. Example more than 1 type of alarm may indicate that someone is
     * tampering with the camera.
     */
    public void changeAlarmState(String thisAlarmsChannel, OnOffType state) {
        updateState(thisAlarmsChannel, state);
    }

    public void motionDetected(String thisAlarmsChannel) {
        updateState(CHANNEL_LAST_MOTION_TYPE, new StringType(thisAlarmsChannel));
        updateState(thisAlarmsChannel, OnOffType.ON);
        motionDetected = true;
        if (streamingAutoFps) {
            startSnapshotPolling();
        }
        if (cameraEntity.getUpdateImageWhen().contains("2")) {
            if (!firstMotionAlarm) {
                if (!snapshotUri.isEmpty()) {
                    sendHttpGET(snapshotUri);
                }
                firstMotionAlarm = true;// reset back to false when the jpg arrives.
            }
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // During Motion Alarms
            if (!snapshotPolling) {
                startSnapshotPolling();
            }
            firstMotionAlarm = true;
            motionAlarmUpdateSnapshot = true;
        }
    }

    public void audioDetected() {
        updateState(CHANNEL_AUDIO_ALARM, OnOffType.ON);
        if (cameraEntity.getUpdateImageWhen().contains("3")) {
            if (!firstAudioAlarm) {
                if (!snapshotUri.isEmpty()) {
                    sendHttpGET(snapshotUri);
                }
                firstAudioAlarm = true;// reset back to false when the jpg arrives.
            }
        } else if (cameraEntity.getUpdateImageWhen().contains("5")) {// During audio alarms
            firstAudioAlarm = true;
            audioAlarmUpdateSnapshot = true;
        }
    }

    public void noAudioDetected() {
        setChannelState(CHANNEL_AUDIO_ALARM, OnOffType.OFF);
        firstAudioAlarm = false;
        audioAlarmUpdateSnapshot = false;
    }

    public void recordMp4(String filename, int seconds) {
        mp4Filename = filename;
        mp4RecordTime = seconds;
        setupFfmpegFormat(FFmpegFormat.RECORD);
        setChannelState(CHANNEL_RECORDING_MP4, new DecimalType(seconds));
    }

    public void recordGif(String filename, int seconds) {
        gifFilename = filename;
        gifRecordTime = seconds;
        if (cameraEntity.getGifPreroll() > 0) {
            snapCount = seconds;
        } else {
            setupFfmpegFormat(FFmpegFormat.GIF);
        }
        setChannelState(CHANNEL_RECORDING_GIF, new DecimalType(seconds));
    }

    public String returnValueFromString(String rawString, String searchedString) {
        String result;
        int index = rawString.indexOf(searchedString);
        if (index != -1) // -1 means "not found"
        {
            result = rawString.substring(index + searchedString.length(), rawString.length());
            index = result.indexOf("\r\n"); // find a carriage return to find the end of the value.
            if (index == -1) {
                return result; // Did not find a carriage return.
            } else {
                return result.substring(0, index);
            }
        }
        return ""; // Did not find the String we were searching for
    }

    private void sendPTZRequest() {
        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.AbsoluteMove);
    }

    public void start(OnOffType command) {
        Ffmpeg localHLS;
        if (OnOffType.ON.equals(command)) {
            localHLS = ffmpegHLS;
            if (localHLS == null) {
                setupFfmpegFormat(FFmpegFormat.HLS);
                localHLS = ffmpegHLS;
            }
            if (localHLS != null) {
                localHLS.setKeepAlive(-1);// Now will run till manually stopped.
                localHLS.startConverting();
            }
        } else {
            localHLS = ffmpegHLS;
            if (localHLS != null) {
                // Still runs but will be able to auto stop when the HLS stream is no longer used.
                localHLS.setKeepAlive(1);
            }
        }
    }

    public void pollImage(OnOffType command) {
        if (OnOffType.ON.equals(command)) {
            if (snapshotUri.isEmpty()) {
                ffmpegSnapshotGeneration = true;
                setupFfmpegFormat(FFmpegFormat.SNAPSHOT);
                updateImageChannel = false;
            } else {
                updateImageChannel = true;
                sendHttpGET(snapshotUri);// Allows this to change Image FPS on demand
            }
        } else {
            Ffmpeg localSnaps = ffmpegSnapshot;
            if (localSnaps != null) {
                localSnaps.stopConverting();
                ffmpegSnapshotGeneration = false;
            }
            updateImageChannel = false;
        }
    }

    protected List<CameraAction> getCameraActions() {
        CameraAction.CameraActionsBuilder cameraActionsBuilder = CameraAction.builder();
        if (onvifCamera.supportsPTZ()) {
            cameraActionsBuilder.add(CHANNEL_PAN, UIFieldType.Slider, param -> {
                updateState(CHANNEL_PAN, new DecimalType(Math.round(onvifCamera.getAbsolutePan())));
            });
            cameraActionsBuilder.add(CHANNEL_TILT, UIFieldType.Slider, param -> {
                updateState(CHANNEL_TILT, new DecimalType(Math.round(onvifCamera.getAbsoluteTilt())));
            });
            cameraActionsBuilder.add(CHANNEL_ZOOM, UIFieldType.Slider, param -> {
                updateState(CHANNEL_ZOOM, new DecimalType(Math.round(onvifCamera.getAbsoluteZoom())));
            });
            cameraActionsBuilder.add(CHANNEL_GOTO_PRESET, UIFieldType.Slider, param -> {
                onvifCamera.sendPTZRequest(OnvifConnection.RequestType.GetPresets);
            });
        }


        return cameraActionsBuilder.get();
    }

    public void setMp4HistoryLength(int length) {
        if (length == 0) {
            mp4HistoryLength = 0;
            mp4History = "";
            setChannelState(CHANNEL_MP4_HISTORY, new StringType(mp4History));
        }
    }

    public void setGifHistoryLength(int length) {
        if (length == 0) {
            gifHistoryLength = 0;
            gifHistory = "";
            setChannelState(CHANNEL_GIF_HISTORY, new StringType(gifHistory));
        }
    }

    public void setFfmpegMotionControl(int threshold) {
        if (threshold == 0) {
            motionAlarmEnabled = false;
            noMotionDetected(CHANNEL_FFMPEG_MOTION_ALARM);
        } else {
            motionAlarmEnabled = true;
            motionThreshold = Double.valueOf(threshold);
            motionThreshold = motionThreshold / 10000;
        }
        setupFfmpegFormat(FFmpegFormat.RTSP_ALARMS);
    }

    public void externalMotion(boolean on) {
        if (on) {
            motionDetected(CHANNEL_EXTERNAL_MOTION);
        } else {
            noMotionDetected(CHANNEL_EXTERNAL_MOTION);
        }
    }

    public void gotoPreset(String command) {
        if (onvifCamera.supportsPTZ()) {
            onvifCamera.gotoPreset(Integer.parseInt(command));
        }
    }

    public void pan(String command) {
        if (onvifCamera.supportsPTZ()) {
            if (command.equals("INCREASE") || command.equals("DECREASE")) {
                if (command.equals("INCREASE")) {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveLeft);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveLeft);
                    }
                } else {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveRight);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveRight);
                    }
                }
                return;
            } else if (command.equals("OFF")) {
                onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
                return;
            }
            onvifCamera.setAbsolutePan(Float.valueOf(command));
            threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
        }
    }

    public void tilt(String command) {
        if (onvifCamera.supportsPTZ()) {
            if (command.equals("INCREASE") || command.equals("DECREASE")) {
                if (command.equals("INCREASE")) {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveUp);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveUp);
                    }
                } else {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveDown);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveDown);
                    }
                }
                return;
            } else if (OnOffType.OFF.equals(command)) {
                onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
                return;
            }
            onvifCamera.setAbsoluteTilt(Float.valueOf(command.toString()));
            threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
        }
    }

    public void zoom(String command) {
        if (onvifCamera.supportsPTZ()) {
            if (command.equals("INCREASE") || command.equals("DECREASE")) {
                if (command.equals("INCREASE")) {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveIn);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveIn);
                    }
                } else {
                    if (cameraEntity.isPtzContinuous()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.ContinuousMoveOut);
                    } else {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.RelativeMoveOut);
                    }
                }
                return;
            } else if (OnOffType.OFF.equals(command)) {
                onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
                return;
            }
            onvifCamera.setAbsoluteZoom(Float.valueOf(command.toString()));
            threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
        }
    }

    public void handleCommand(ChannelUID channelUID, String command) {
        // TODO:????????????
        //*if (command.equals("RefreshType")) {
            /*switch (channelUID.getId()) {
                case CHANNEL_PAN:
                    if (onvifCamera.supportsPTZ()) {
                        updateState(CHANNEL_PAN, new DecimalType(Math.round(onvifCamera.getAbsolutePan())));
                    }
                    return;
                case CHANNEL_TILT:
                    if (onvifCamera.supportsPTZ()) {
                        updateState(CHANNEL_TILT, new DecimalType(Math.round(onvifCamera.getAbsoluteTilt())));
                    }
                    return;
                case CHANNEL_ZOOM:
                    if (onvifCamera.supportsPTZ()) {
                        updateState(CHANNEL_ZOOM, new DecimalType(Math.round(onvifCamera.getAbsoluteZoom())));
                    }
                    return;
                case CHANNEL_GOTO_PRESET:
                    if (onvifCamera.supportsPTZ()) {
                        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.GetPresets);
                    }
                    return;
            }*/

        // commands and refresh now get passed to brand handlers
        /*switch (cameraEntity.getCameraType()) {
            case amcrest:
                AmcrestHandler amcrestHandler = new AmcrestHandler(this);
                amcrestHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = amcrestHandler.getLowPriorityRequests();
                }
                break;
            case dahua:
                DahuaHandler dahuaHandler = new DahuaHandler(this, cameraEntity.getNvrChannel());
                dahuaHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = dahuaHandler.getLowPriorityRequests();
                }
                break;
            case doorbird:
                DoorBirdHandler doorBirdHandler = new DoorBirdHandler(this);
                doorBirdHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = doorBirdHandler.getLowPriorityRequests();
                }
                break;
            case hikvision:
                HikvisionHandler hikvisionHandler = new HikvisionHandler(this, cameraEntity.getNvrChannel());
                hikvisionHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = hikvisionHandler.getLowPriorityRequests();
                }
                break;
            case foscam:
                FoscamHandler foscamHandler = new FoscamHandler(this, cameraEntity.getUser(),
                        cameraEntity.getPassword());
                foscamHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = foscamHandler.getLowPriorityRequests();
                }
                break;
            case instar:
                InstarHandler instarHandler = new InstarHandler(this);
                instarHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = instarHandler.getLowPriorityRequests();
                }
                break;
            default:
                HttpOnlyHandler defaultHandler = new HttpOnlyHandler(this);
                defaultHandler.handleCommand(channelUID, command);
                if (lowPriorityRequests.isEmpty()) {
                    lowPriorityRequests = defaultHandler.getLowPriorityRequests();
                }
                break;
        }*/
    }

    public void setChannelState(String channelToUpdate, State valueOf) {
        updateState(channelToUpdate, valueOf);
    }

    void bringCameraOnline() {
        isOnline = true;
        updateStatus(Status.ONLINE, null);
        groupTracker.onlineCameraMap.put(cameraEntity.getEntityID(), this);
        Future<?> localFuture = cameraConnectionJob;
        if (localFuture != null) {
            localFuture.cancel(false);
        }

        if (cameraEntity.getGifPreroll() > 0 || cameraEntity.getUpdateImageWhen().contains("1")) {
            snapshotPolling = true;
            snapshotJob = threadPool.scheduleAtFixedRate(this::snapshotRunnable, 1000, cameraEntity.getJpegPollTime(),
                    TimeUnit.MILLISECONDS);
        }

        pollCameraJob = threadPool.scheduleWithFixedDelay(this::pollCameraRunnable, 1000, 8000, TimeUnit.MILLISECONDS);

        if (!rtspUri.isEmpty()) {
            updateState(CHANNEL_RTSP_URL, new StringType(rtspUri));
        }
        this.pollImage(OnOffType.valueOf(updateImageChannel));
        if (!groupTracker.listOfGroupHandlers.isEmpty()) {
            for (IpCameraGroupHandler handle : groupTracker.listOfGroupHandlers) {
                handle.cameraOnline(cameraEntity);
            }
        }
    }

    void snapshotIsFfmpeg() {
        bringCameraOnline();
        snapshotUri = "";// ffmpeg is a valid option. Simplify further checks.
        log.debug(
                "Binding has no snapshot url. Will use your CPU and FFmpeg to create snapshots from the cameras RTSP.");
        if (!rtspUri.isEmpty()) {
            updateImageChannel = false;
            ffmpegSnapshotGeneration = true;
            setupFfmpegFormat(FFmpegFormat.SNAPSHOT);
            this.pollImage(OnOffType.ON);
        } else {
            cameraConfigError("Binding can not find a RTSP url for this camera, please provide a FFmpeg Input URL.");
        }
    }

    void pollingCameraConnection() {
        if (cameraEntity.getCameraType() == CameraType.generic) {
            if (rtspUri.isEmpty()) {
                log.warn("Binding has not been supplied with a FFmpeg Input URL, so some features will not work.");
            }
            if (snapshotUri.isEmpty() || snapshotUri.equals("ffmpeg")) {
                snapshotIsFfmpeg();
            } else {
                sendHttpRequest("GET", snapshotUri, null);
            }
            return;
        }
        if (!onvifCamera.isConnected()) {
            log.debug("About to connect to the IP Camera using the ONVIF PORT at IP:{}:{}", cameraEntity.getIp(),
                    cameraEntity.getOnvifPort());
            onvifCamera.connect(cameraEntity.getCameraType() == CameraType.onvif);
        }
        if (snapshotUri.equals("ffmpeg")) {
            snapshotIsFfmpeg();
        } else if (!snapshotUri.isEmpty()) {
            sendHttpRequest("GET", snapshotUri, null);
        } else if (!rtspUri.isEmpty()) {
            snapshotIsFfmpeg();
        } else {
            updateStatus(Status.OFFLINE, "Camera failed to report a valid Snaphot and/or RTSP URL. See readme on how to use the SNAPSHOT_URL_OVERRIDE feature.");
        }
    }

    public void cameraConfigError(String reason) {
        // wont try to reconnect again due to a config error being the cause.
        updateStatus(Status.OFFLINE, reason);
        dispose();
    }

    private void updateStatus(Status status, String message) {
        log.info("Camera update status: <{}>. <{}>", status, message);
        entityContext.updateDeviceStatus(cameraEntity, status, message);
    }

    public void cameraCommunicationError(String reason) {
        // will try to reconnect again as camera may be rebooting.
        updateStatus(Status.OFFLINE, reason);
        if (isOnline) {// if already offline dont try reconnecting in 6 seconds, we want 30sec wait.
            resetAndRetryConnecting();
        }
    }

    boolean streamIsStopped(String url) {
        ChannelTracking channelTracking = channelTrackingMap.get(url);
        if (channelTracking != null) {
            return !channelTracking.getChannel().isActive(); // stream is running.
        }
        return true; // Stream stopped or never started.
    }

    void snapshotRunnable() {
        // Snapshot should be first to keep consistent time between shots
        sendHttpGET(snapshotUri);
        if (snapCount > 0) {
            if (--snapCount == 0) {
                setupFfmpegFormat(FFmpegFormat.GIF);
            }
        }
    }

    public void stopSnapshotPolling() {
        Future<?> localFuture;
        if (!streamingSnapshotMjpeg && cameraEntity.getGifPreroll() == 0
                && !cameraEntity.getUpdateImageWhen().contains("1")) {
            snapshotPolling = false;
            localFuture = snapshotJob;
            if (localFuture != null) {
                localFuture.cancel(true);
            }
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // only during Motion Alarms
            snapshotPolling = false;
            localFuture = snapshotJob;
            if (localFuture != null) {
                localFuture.cancel(true);
            }
        }
    }

    public void startSnapshotPolling() {
        if (snapshotPolling || ffmpegSnapshotGeneration) {
            return; // Already polling or creating with FFmpeg from RTSP
        }
        if (streamingSnapshotMjpeg || streamingAutoFps) {
            snapshotPolling = true;
            snapshotJob = threadPool.scheduleAtFixedRate(this::snapshotRunnable, 200, cameraEntity.getJpegPollTime(),
                    TimeUnit.MILLISECONDS);
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // During Motion Alarms
            snapshotPolling = true;
            snapshotJob = threadPool.scheduleAtFixedRate(this::snapshotRunnable, 200, cameraEntity.getJpegPollTime(),
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Polls every 8 seconds, to check camera is still ONLINE and keep mjpeg and alarm streams open and more.
     */
    void pollCameraRunnable() {
        // Snapshot should be first to keep consistent time between shots
        if (streamingAutoFps) {
            updateAutoFps = true;
            if (!snapshotPolling && !ffmpegSnapshotGeneration) {
                // Dont need to poll if creating from RTSP stream with FFmpeg or we are polling at full rate already.
                sendHttpGET(snapshotUri);
            }
        } else if (!snapshotUri.isEmpty() && !snapshotPolling) {// we need to check camera is still online.
            sendHttpGET(snapshotUri);
        }
        // NOTE: Use lowPriorityRequests if get request is not needed every poll.
        if (!lowPriorityRequests.isEmpty()) {
            if (lowPriorityCounter >= lowPriorityRequests.size()) {
                lowPriorityCounter = 0;
            }
            sendHttpGET(lowPriorityRequests.get(lowPriorityCounter++));
        }
        // what needs to be done every poll//
        switch (cameraEntity.getCameraType()) {
            case generic:
                break;
            case onvif:
                if (!onvifCamera.isConnected()) {
                    onvifCamera.connect(true);
                }
                break;
            case instar:
                noMotionDetected(CHANNEL_MOTION_ALARM);
                noMotionDetected(CHANNEL_PIR_ALARM);
                noAudioDetected();
                break;
            case hikvision:
                if (streamIsStopped("/ISAPI/Event/notification/alertStream")) {
                    log.info("The alarm stream was not running for camera {}, re-starting it now",
                            cameraEntity.getIp());
                    sendHttpGET("/ISAPI/Event/notification/alertStream");
                }
                break;
            case amcrest:
                sendHttpGET("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion");
                sendHttpGET("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation");
                break;
            case dahua:
                // Check for alarms, channel for NVRs appears not to work at filtering.
                if (streamIsStopped("/cgi-bin/eventManager.cgi?action=attach&codes=[All]")) {
                    log.info("The alarm stream was not running for camera {}, re-starting it now",
                            cameraEntity.getIp());
                    sendHttpGET("/cgi-bin/eventManager.cgi?action=attach&codes=[All]");
                }
                break;
            case doorbird:
                // Check for alarms, channel for NVRs appears not to work at filtering.
                if (streamIsStopped("/bha-api/monitor.cgi?ring=doorbell,motionsensor")) {
                    log.info("The alarm stream was not running for camera {}, re-starting it now",
                            cameraEntity.getIp());
                    sendHttpGET("/bha-api/monitor.cgi?ring=doorbell,motionsensor");
                }
                break;
        }
        Ffmpeg localHLS = ffmpegHLS;
        if (localHLS != null) {
            localHLS.checkKeepAlive();
        }
        if (openChannels.size() > 18) {
            log.debug("There are {} open Channels being tracked.", openChannels.size());
            cleanChannels();
        }
    }

    public void initialize() {
        snapshotUri = getCorrectUrlFormat(cameraEntity.getSnapshotUrl());
        mjpegUri = getCorrectUrlFormat(cameraEntity.getMjpegUrl());
        rtspUri = cameraEntity.getFfmpegInput();

        if (cameraEntity.getServerPort() < 1) {
            log.warn(
                    "The Server Port is not set to a valid number which disables a lot of binding features. See readme for more info.");
        } else if (cameraEntity.getServerPort() < 1025) {
            log.warn("The Server Port is <= 1024 and may cause permission errors under Linux, try a higher number.");
        }

        // Known cameras will connect quicker if we skip ONVIF questions.
        switch (cameraEntity.getCameraType()) {
            case amcrest:
            case dahua:
                if (mjpegUri.isEmpty()) {
                    mjpegUri = "/cgi-bin/mjpg/video.cgi?channel=" + cameraEntity.getNvrChannel() + "&subtype=1";
                }
                if (snapshotUri.isEmpty()) {
                    snapshotUri = "/cgi-bin/snapshot.cgi?channel=" + cameraEntity.getNvrChannel();
                }
                break;
            case doorbird:
                if (mjpegUri.isEmpty()) {
                    mjpegUri = "/bha-api/video.cgi";
                }
                if (snapshotUri.isEmpty()) {
                    snapshotUri = "/bha-api/image.cgi";
                }
                break;
            case foscam:
                // Foscam needs any special char like spaces (%20) to be encoded for URLs.
                cameraEntity.setUser(Helper.encodeSpecialChars(cameraEntity.getUser()));
                cameraEntity.setPassword(Helper.encodeSpecialChars(cameraEntity.getPassword()));
                if (mjpegUri.isEmpty()) {
                    mjpegUri = "/cgi-bin/CGIStream.cgi?cmd=GetMJStream&usr=" + cameraEntity.getUser() + "&pwd="
                            + cameraEntity.getPassword();
                }
                if (snapshotUri.isEmpty()) {
                    snapshotUri = "/cgi-bin/CGIProxy.fcgi?usr=" + cameraEntity.getUser() + "&pwd="
                            + cameraEntity.getPassword() + "&cmd=snapPicture2";
                }
                break;
            case hikvision:// The 02 gives you the first sub stream which needs to be set to MJPEG
                if (mjpegUri.isEmpty()) {
                    mjpegUri = "/ISAPI/Streaming/channels/" + cameraEntity.getNvrChannel() + "02" + "/httppreview";
                }
                if (snapshotUri.isEmpty()) {
                    snapshotUri = "/ISAPI/Streaming/channels/" + cameraEntity.getNvrChannel() + "01/picture";
                }
                break;
            case instar:
                if (snapshotUri.isEmpty()) {
                    snapshotUri = "/tmpfs/snap.jpg";
                }
                if (mjpegUri.isEmpty()) {
                    mjpegUri = "/mjpegstream.cgi?-chn=12";
                }
                break;
        }

        // Onvif and Instar event handling needs the host IP and the server started.
        if (cameraEntity.getServerPort() > 0) {
            startStreamServer();
        }

        if (cameraEntity.getCameraType() != CameraType.generic) {
            onvifCamera = new OnvifConnection(this, cameraEntity.getIp() + ":" + cameraEntity.getOnvifPort(),
                    cameraEntity.getUser(), cameraEntity.getPassword());
            onvifCamera.setSelectedMediaProfile(cameraEntity.getOnvifMediaProfile());
            // Only use ONVIF events if it is not an API camera.
            onvifCamera.connect(cameraEntity.getCameraType() == CameraType.onvif);
        }

        // for poll times above 9 seconds don't display a warning about the Image channel.
        if (9000 <= cameraEntity.getJpegPollTime() && cameraEntity.getUpdateImageWhen().contains("1")) {
            log.warn(
                    "The Image channel is set to update more often than 8 seconds. This is not recommended. The Image channel is best used only for higher poll times. See the readme file on how to display the cameras picture for best results or use a higher poll time.");
        }
        // Waiting 3 seconds for ONVIF to discover the urls before running.
        cameraConnectionJob = threadPool.scheduleWithFixedDelay(this::pollingCameraConnection, 4, 30, TimeUnit.SECONDS);
    }

    // What the camera needs to re-connect if the initialize() is not called.
    private void resetAndRetryConnecting() {
        dispose();
        initialize();
    }

    public void dispose() {
        isOnline = false;
        snapshotPolling = false;
        onvifCamera.disconnect();
        Future<?> localFuture = pollCameraJob;
        if (localFuture != null) {
            localFuture.cancel(true);
        }
        localFuture = snapshotJob;
        if (localFuture != null) {
            localFuture.cancel(true);
        }
        localFuture = cameraConnectionJob;
        if (localFuture != null) {
            localFuture.cancel(true);
        }
        threadPool.shutdown();
        threadPool = Executors.newScheduledThreadPool(4);

        groupTracker.onlineCameraMap.remove(cameraEntity.getEntityID());
        // inform all group handlers that this camera has gone offline
        for (IpCameraGroupHandler handle : groupTracker.listOfGroupHandlers) {
            handle.cameraOffline(this);
        }
        basicAuth = ""; // clear out stored Password hash
        useDigestAuth = false;
        stopStreamServer();
        openChannels.close();

        Ffmpeg localFfmpeg = ffmpegHLS;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        localFfmpeg = ffmpegRecord;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        localFfmpeg = ffmpegGIF;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        localFfmpeg = ffmpegRtspHelper;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        localFfmpeg = ffmpegMjpeg;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        localFfmpeg = ffmpegSnapshot;
        if (localFfmpeg != null) {
            localFfmpeg.stopConverting();
        }
        channelTrackingMap.clear();
    }

    public String getWhiteList() {
        return cameraEntity.getIpWhitelist();
    }

    public Logger getLog() {
        return log;
    }

    /*@Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(IpCameraActions.class);
    }*/
}

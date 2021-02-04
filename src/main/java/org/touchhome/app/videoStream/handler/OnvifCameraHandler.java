package org.touchhome.app.videoStream.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.entity.OnvifCameraType;
import org.touchhome.app.videoStream.ffmpeg.Ffmpeg;
import org.touchhome.app.videoStream.onvif.OnvifConnection;
import org.touchhome.app.videoStream.onvif.impl.AmcrestTypeHandler;
import org.touchhome.app.videoStream.onvif.impl.InstarTypeHandler;
import org.touchhome.app.videoStream.onvif.util.ChannelTracking;
import org.touchhome.app.videoStream.onvif.util.Helper;
import org.touchhome.app.videoStream.onvif.util.MyNettyAuthHandler;
import org.touchhome.app.videoStream.ui.*;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.RawType;
import org.touchhome.bundle.api.measure.State;
import org.touchhome.bundle.api.model.Status;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;
import static org.touchhome.bundle.api.util.TouchHomeUtils.MACHINE_IP_ADDRESS;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
public class OnvifCameraHandler extends BaseFfmpegCameraHandler<OnvifCameraEntity> {

    private ScheduledExecutorService threadPool;
    // private GroupTracker groupTracker;

    // ChannelGroup is thread safe
    public final ChannelGroup mjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup snapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup autoSnapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private boolean streamingAutoFps = false;

    private EntityContextBGP.ThreadContext<Void> pollCameraJob = null;
    private ScheduledFuture<?> snapshotJob = null;
    private Bootstrap mainBootstrap;

    private EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();
    private FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), "");

    private byte lowPriorityCounter = 0;
    public Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();
    public List<String> lowPriorityRequests;

    // basicAuth MUST remain private as it holds the cameraEntity.getPassword()
    private String basicAuth = "";
    public boolean useBasicAuth = false;
    public boolean useDigestAuth = false;
    public String snapshotUri = "";
    public String mjpegUri = "";
    private Object firstStreamedMsg = new Object();

    public boolean audioAlarmUpdateSnapshot = false;
    private boolean motionAlarmUpdateSnapshot = false;
    private boolean firstAudioAlarm = false;
    private boolean firstMotionAlarm = false;

    private boolean streamingSnapshotMjpeg = false;
    private boolean updateAutoFps = false;

    public boolean snapshotPolling = false;
    public OnvifConnection onvifCamera = new OnvifConnection(this, "", "", "");

    // These methods handle the response from all camera brands, nothing specific to 1 brand.
    private class CommonCameraHandler extends ChannelDuplexHandler {
        private int bytesToReceive = 0;
        private int bytesAlreadyReceived = 0;
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
                                        bytesToReceive = Integer.parseInt(response.headers().getAsString(name));
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
                                if (bytesToReceive == 0) {
                                    bytesToReceive = 768000; // 0.768 Mbyte when no Content-Length is sent
                                    log.debug("Camera has no Content-Length header, we have to guess how much RAM.");
                                }
                                incomingJpeg = new byte[bytesToReceive];
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
                                incomingJpeg[bytesAlreadyReceived++] = content.content().getByte(i);
                            }
                            if (content instanceof LastHttpContent) {
                                processSnapshot(incomingJpeg);
                                // testing next line and if works need to do a full cleanup of this function.
                                closeConnection = true;
                                if (closeConnection) {
                                    ctx.close();
                                } else {
                                    bytesToReceive = 0;
                                    bytesAlreadyReceived = 0;
                                }
                            }
                        } else { // incomingMessage that is not an IMAGE
                            if (incomingMessage.isEmpty()) {
                                incomingMessage = content.content().toString(CharsetUtil.UTF_8);
                            } else {
                                incomingMessage += content.content().toString(CharsetUtil.UTF_8);
                            }
                            bytesAlreadyReceived = incomingMessage.length();
                            if (content instanceof LastHttpContent) {
                                // If it is not an image send it on to the next handler//
                                if (bytesAlreadyReceived != 0) {
                                    reply = incomingMessage;
                                    super.channelRead(ctx, reply);
                                }
                            }
                            // Alarm Streams never have a LastHttpContent as they always stay open//
                            else if (contentType.contains("multipart")) {
                                if (bytesAlreadyReceived != 0) {
                                    reply = incomingMessage;
                                    incomingMessage = "";
                                    bytesToReceive = 0;
                                    bytesAlreadyReceived = 0;
                                    super.channelRead(ctx, reply);
                                }
                            }
                            // Foscam needs this as will other cameras with chunks//
                            if (isChunked && bytesAlreadyReceived != 0) {
                                log.debug("Reply is chunked.");
                                reply = incomingMessage;
                                super.channelRead(ctx, reply);
                            }
                        }
                    }
                } else { // msg is not HttpContent
                    // Foscam cameras need this
                    if (!contentType.contains("image/jp") && bytesAlreadyReceived != 0) {
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
                log.debug("Camera sent {} bytes when the content-length header was {}.", bytesAlreadyReceived,
                        bytesToReceive);
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

    public OnvifCameraHandler(OnvifCameraEntity cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
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

                @SneakyThrows
                @Override
                public void initChannel(SocketChannel socketChannel) {
                    // HIK Alarm stream needs > 9sec idle to stop stream closing
                    socketChannel.pipeline().addLast(new IdleStateHandler(18, 0, 0));
                    socketChannel.pipeline().addLast(new HttpClientCodec());
                    socketChannel.pipeline().addLast(AUTH_HANDLER,
                            new MyNettyAuthHandler(cameraEntity.getUser(), cameraEntity.getPassword(), OnvifCameraHandler.this));
                    socketChannel.pipeline().addLast(COMMON_HANDLER, new CommonCameraHandler());

                    socketChannel.pipeline().addLast(cameraEntity.getCameraType().getHandlerName(),
                            cameraEntity.getCameraTypeHandler());
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
                                AmcrestTypeHandler amcrestHandler = (AmcrestTypeHandler) ch.pipeline().get(AMCREST_HANDLER);
                                amcrestHandler.setURL(httpRequestURL);
                                break;
                            case instar:
                                InstarTypeHandler instarHandler = (InstarTypeHandler) ch.pipeline().get(INSTAR_HANDLER);
                                instarHandler.setURL(httpRequestURL);
                                break;
                        }
                        ch.writeAndFlush(request);
                    } else { // an error occurred
                        restart("Connection Timeout: Check your IP and PORT are correct and the camera can be reached.", null);
                    }
                });
    }

    @Override
    protected void streamServerStarted() {
        if (cameraEntity.getCameraType() == OnvifCameraType.instar) {
            log.info("Setting up the Alarm Server settings in the camera now");
            sendHttpGET("/param.cgi?cmd=setmdalarm&-aname=server2&-switch=on&-interval=1&cmd=setalarmserverattr&-as_index=3&-as_server="
                    + MACHINE_IP_ADDRESS + "&-as_port=" + serverPort + "&-as_path=/instar&-as_queryattr1=&-as_queryval1=&-as_queryattr2=&-as_queryval2=&-as_queryattr3=&-as_queryval3=&-as_activequery=1&-as_auth=0&-as_query1=0&-as_query2=0&-as_query3=0");
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
                log.info("All snapshots.mjpeg streams have stopped.");
            } else if (streamingAutoFps && autoSnapshotMjpegChannelGroup.isEmpty()) {
                streamingAutoFps = false;
                stopSnapshotPolling();
                log.info("All autofps.mjpeg streams have stopped.");
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
                    startMJPEGRecord();
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
                log.info("All ipcamera.mjpeg streams have stopped.");
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

    @Override
    protected String getFFMPEGInputOptions() {
        String inputOptions = cameraEntity.getFfmpegInputOptions();
        if (rtspUri.isEmpty()) {
            log.warn("The camera tried to use a FFmpeg feature when no valid input for FFmpeg is provided.");
            return null;
        }
        if (rtspUri.toLowerCase().contains("rtsp")) {
            if (inputOptions.isEmpty()) {
                return "-rtsp_transport tcp";
            }
        }
        return inputOptions;
    }

    @Override
    public void motionDetected(String key) {
        super.motionDetected(key);
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

    @Override
    protected BaseCameraStreamServerHandler createCameraStreamServerHandler() {
        return new OnvifCameraStreamHandler(this);
    }

    @Override
    public void noMotionDetected(String key) {
        super.noMotionDetected(key);
        firstMotionAlarm = false;
        motionAlarmUpdateSnapshot = false;
        if (streamingAutoFps) {
            stopSnapshotPolling();
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // During Motion Alarms
            stopSnapshotPolling();
        }
    }

    @Override
    public void audioDetected() {
        super.audioDetected();
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

    @Override
    public void noAudioDetected() {
        super.noAudioDetected();
        firstAudioAlarm = false;
        audioAlarmUpdateSnapshot = false;
    }

    @Override
    public void processSnapshot(byte[] incomingSnapshot) {
        super.processSnapshot(incomingSnapshot);

        if (streamingSnapshotMjpeg) {
            sendMjpegFrame(incomingSnapshot, snapshotMjpegChannelGroup);
        }
        if (streamingAutoFps) {
            if (motionDetected) {
                sendMjpegFrame(incomingSnapshot, autoSnapshotMjpegChannelGroup);
            } else if (updateAutoFps) {
                // only happens every 8 seconds as some browsers need a frame that often to keep stream alive.
                sendMjpegFrame(incomingSnapshot, autoSnapshotMjpegChannelGroup);
                updateAutoFps = false;
            }
        }

        if (updateImageChannel) {
            attributes.put(CHANNEL_IMAGE, new RawType(incomingSnapshot, "image/jpeg"));
        } else if (firstMotionAlarm || motionAlarmUpdateSnapshot) {
            attributes.put(CHANNEL_IMAGE, new RawType(incomingSnapshot, "image/jpeg"));
            firstMotionAlarm = motionAlarmUpdateSnapshot = false;
        } else if (firstAudioAlarm || audioAlarmUpdateSnapshot) {
            attributes.put(CHANNEL_IMAGE, new RawType(incomingSnapshot, "image/jpeg"));
            firstAudioAlarm = audioAlarmUpdateSnapshot = false;
        }
    }

    public String returnValueFromString(String rawString, String searchedString) {
        String result;
        int index = rawString.indexOf(searchedString);
        if (index != -1) // -1 means "not found"
        {
            result = rawString.substring(index + searchedString.length());
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
                startHLSStream();
                localHLS = ffmpegHLS;
            }
            fireFfmpeg(localHLS, ffmpeg -> {
                ffmpeg.setKeepAlive(-1);// Now will run till manually stopped.
                ffmpeg.startConverting();
            });
        } else {
            // Still runs but will be able to auto stop when the HLS stream is no longer used.
            fireFfmpeg(ffmpegHLS, ffmpeg -> ffmpeg.setKeepAlive(1));
        }
    }

    public void pollImage(boolean on) {
        if (on && !snapshotUri.isEmpty()) {
            updateImageChannel = true;
            sendHttpGET(snapshotUri);// Allows this to change Image FPS on demand
        } else {
            super.pollImage(on);
        }
    }

    public void setFfmpegMotionControl(int threshold) {
        if (threshold == 0) {
            motionAlarmEnabled = false;
            noMotionDetected(CHANNEL_FFMPEG_MOTION_ALARM);
        } else {
            motionAlarmEnabled = true;
            motionThreshold = (double) threshold;
            motionThreshold = motionThreshold / 10000;
        }
        startRtspAlarms();
    }

    public void externalMotion(boolean on) {
        if (on) {
            motionDetected(CHANNEL_EXTERNAL_MOTION);
        } else {
            noMotionDetected(CHANNEL_EXTERNAL_MOTION);
        }
    }

    @UICameraActionGetter(CHANNEL_PAN)
    public DecimalType getPan() {
        return new DecimalType(Math.round(onvifCamera.getAbsolutePan()));
    }

    @UICameraAction(name = CHANNEL_PAN, icon = "fas fa-expand-arrows-alt", type = UICameraAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "LEFT", icon = "fas fa-caret-left")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "RIGHT", icon = "fas fa-caret-right")
    public void setPan(String command) {
        if ("LEFT".equals(command) || "RIGHT".equals(command)) {
            if ("LEFT".equals(command)) {
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
        } else if ("OFF".equals(command)) {
            onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
        }
        onvifCamera.setAbsolutePan(Float.valueOf(command));
        threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
    }

    @UICameraActionGetter(CHANNEL_TILT)
    public DecimalType getTilt() {
        return new DecimalType(Math.round(onvifCamera.getAbsoluteTilt()));
    }

    @UICameraAction(name = CHANNEL_TILT, icon = "fas fa-sort", type = UICameraAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "UP", icon = "fas fa-caret-up")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "DOWN", icon = "fas fa-caret-down")
    public void setTilt(String command) {
        if ("UP".equals(command) || "DOWN".equals(command)) {
            if ("UP".equals(command)) {
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
        } else if ("OFF".equals(command)) {
            onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
        }
        onvifCamera.setAbsoluteTilt(Float.valueOf(command));
        threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
    }

    @UICameraActionGetter(CHANNEL_ZOOM)
    public DecimalType getZoom() {
        return new DecimalType(Math.round(onvifCamera.getAbsoluteZoom()));
    }

    @UICameraAction(name = CHANNEL_ZOOM, icon = "fas fa-search-plus", type = UICameraAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "IN", icon = "fas fa-search-plus")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "OUT", icon = "fas fa-search-minus")
    public void setZoom(String command) {
        if ("IN".equals(command) || "OUT".equals(command)) {
            if ("IN".equals(command)) {
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
        } else if ("OFF".equals(command)) {
            onvifCamera.sendPTZRequest(OnvifConnection.RequestType.Stop);
        }
        onvifCamera.setAbsoluteZoom(Float.valueOf(command));
        threadPool.schedule(this::sendPTZRequest, 500, TimeUnit.MILLISECONDS);
    }

    @UICameraActionGetter(CHANNEL_GOTO_PRESET)
    public DecimalType getGotoPreset() {
        onvifCamera.sendPTZRequest(OnvifConnection.RequestType.GetPresets);
        return new DecimalType(0); //TODO: (DecimalType) channelStates.getOrDefault(CHANNEL_GOTO_PRESET, null);
    }

    @UICameraAction(name = CHANNEL_GOTO_PRESET, icon = "fas fa-location-arrow", min = 1, max = 25, selectReplacer = "Preset %0  ")
    @UICameraActionConditional(SupportPTZ.class)
    public void gotoPreset(int preset) {
        if (onvifCamera.supportsPTZ()) {
            onvifCamera.gotoPreset(preset);
        }
    }

    protected void bringCameraOnline0() {
        //     groupTracker.onlineCameraMap.put(cameraEntity.getEntityID(), this);

        if (cameraEntity.getGifPreroll() > 0 || cameraEntity.getUpdateImageWhen().contains("1")) {
            snapshotPolling = true;
            snapshotJob = threadPool.scheduleWithFixedDelay(this::snapshotRunnable, 1000, cameraEntity.getJpegPollTime(),
                    TimeUnit.MILLISECONDS);
        }

        entityContext.bgp().schedule("poll-camera-" + cameraEntity.getTitle(), 8,
                TimeUnit.SECONDS, this::pollCameraRunnable, true);

        this.pollImage(updateImageChannel);
     /*   if (!groupTracker.listOfGroupHandlers.isEmpty()) {
            for (IpCameraGroupHandler handle : groupTracker.listOfGroupHandlers) {
                handle.cameraOnline(cameraEntity);
            }
        }*/
    }

    @Override
    protected List<CameraAction> getAdditionalCameraActions() {
        return cameraEntity.getCameraTypeHandler().getCameraActions();
    }

    @Override
    public Map<String, State> getAttributes() {
        HashMap<String, State> map = new HashMap<>(super.getAttributes());
        map.putAll(cameraEntity.getCameraTypeHandler().getAttributes());
        return map;
    }

    protected void snapshotIsFfmpeg() {
        bringCameraOnline();
        snapshotUri = "";// ffmpeg is a valid option. Simplify further checks.
        log.info("Binding has no snapshot url. Will use your CPU and FFmpeg to create snapshots from the cameras RTSP.");
        if (!rtspUri.isEmpty()) {
            updateImageChannel = false;
            ffmpegSnapshotGeneration = true;
            this.pollImage(true);
        } else {
            cameraConfigError("Binding can not find a RTSP url for this camera, please provide a FFmpeg Input URL.");
        }
    }

    @Override
    protected void pollingCameraConnection() {
        if (!onvifCamera.isConnected()) {
            log.debug("About to connect to the IP Camera using the ONVIF PORT at IP:{}:{}", cameraEntity.getIp(),
                    cameraEntity.getOnvifPort());
            onvifCamera.connect(cameraEntity.getCameraType() == OnvifCameraType.onvif);
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
                startGiffRecord();
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
            snapshotJob = threadPool.scheduleWithFixedDelay(this::snapshotRunnable, 200, cameraEntity.getJpegPollTime(),
                    TimeUnit.MILLISECONDS);
        } else if (cameraEntity.getUpdateImageWhen().contains("4")) { // During Motion Alarms
            snapshotPolling = true;
            snapshotJob = threadPool.scheduleWithFixedDelay(this::snapshotRunnable, 200, cameraEntity.getJpegPollTime(),
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
        if (lowPriorityRequests != null && !lowPriorityRequests.isEmpty()) {
            if (lowPriorityCounter >= lowPriorityRequests.size()) {
                lowPriorityCounter = 0;
            }
            sendHttpGET(lowPriorityRequests.get(lowPriorityCounter++));
        }
        // what needs to be done every poll//
        switch (cameraEntity.getCameraType()) {
            /*case generic:
                break;*/
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

    @Override
    protected void initialize0() {
        super.initialize0();
        snapshotUri = getCorrectUrlFormat(cameraEntity.getSnapshotUrl());
        mjpegUri = getCorrectUrlFormat(cameraEntity.getMjpegUrl());
        lowPriorityRequests = cameraEntity.getCameraTypeHandler().getLowPriorityRequests();
        threadPool = Executors.newScheduledThreadPool(4);

        switch (cameraEntity.getCameraType()) {
            case amcrest:
            case dahua:
                mjpegUri = StringUtils.defaultIfEmpty(mjpegUri, "/cgi-bin/mjpg/video.cgi?channel=" + cameraEntity.getNvrChannel() + "&subtype=1");
                snapshotUri = StringUtils.defaultIfEmpty(snapshotUri, "/cgi-bin/snapshot.cgi?channel=" + cameraEntity.getNvrChannel());
                break;
            case doorbird:
                mjpegUri = StringUtils.defaultIfEmpty(mjpegUri, "/bha-api/video.cgi");
                snapshotUri = StringUtils.defaultIfEmpty(snapshotUri, "/bha-api/image.cgi");
                break;
            case foscam:
                // Foscam needs any special char like spaces (%20) to be encoded for URLs.
                cameraEntity.setUser(Helper.encodeSpecialChars(cameraEntity.getUser()));
                cameraEntity.setPassword(Helper.encodeSpecialChars(cameraEntity.getPassword()));
                mjpegUri = StringUtils.defaultIfEmpty(mjpegUri, "/cgi-bin/CGIStream.cgi?cmd=GetMJStream&usr=" + cameraEntity.getUser() + "&pwd="
                        + cameraEntity.getPassword());
                snapshotUri = StringUtils.defaultIfEmpty(snapshotUri, "/cgi-bin/CGIProxy.fcgi?usr=" + cameraEntity.getUser() + "&pwd="
                        + cameraEntity.getPassword() + "&cmd=snapPicture2");
                break;
            case hikvision:// The 02 gives you the first sub stream which needs to be set to MJPEG
                mjpegUri = StringUtils.defaultIfEmpty(mjpegUri, "/ISAPI/Streaming/channels/" + cameraEntity.getNvrChannel() + "02" + "/httppreview");
                snapshotUri = StringUtils.defaultIfEmpty(snapshotUri, "/ISAPI/Streaming/channels/" + cameraEntity.getNvrChannel() + "01/picture");
                break;
            case instar:
                mjpegUri = StringUtils.defaultIfEmpty(mjpegUri, "/tmpfs/snap.jpg");
                snapshotUri = StringUtils.defaultIfEmpty(snapshotUri, "/mjpegstream.cgi?-chn=12");
                break;
        }

        onvifCamera = new OnvifConnection(this, cameraEntity.getIp() + ":" + cameraEntity.getOnvifPort(),
                cameraEntity.getUser(), cameraEntity.getPassword());
        onvifCamera.setSelectedMediaProfile(cameraEntity.getOnvifMediaProfile());
        // Only use ONVIF events if it is not an API camera.
        onvifCamera.connect(cameraEntity.getCameraType() == OnvifCameraType.onvif);

        // for poll times above 9 seconds don't display a warning about the Image channel.
        if (cameraEntity.getJpegPollTime() <= 9000 && cameraEntity.getUpdateImageWhen().contains("1")) {
            log.warn("The Image channel is set to update more often than 8 seconds. This is not recommended. The Image channel is best used only for higher poll times. See the readme file on how to display the cameras picture for best results or use a higher poll time.");
        }
    }

    @Override
    protected String createRtspUri() {
        return cameraEntity.getFfmpegInput();
    }

    @Override
    protected void dispose0() {
        super.dispose0();
        snapshotPolling = false;
        onvifCamera.disconnect();
        pollCameraJob.cancel();

        if (snapshotJob != null) {
            snapshotJob.cancel(true);
        }
        threadPool.shutdown();
//        groupTracker.onlineCameraMap.remove(cameraEntity.getEntityID());
        // inform all group handlers that this camera has gone offline
  /*      for (IpCameraGroupHandler handle : groupTracker.listOfGroupHandlers) {
            handle.cameraOffline(this);
        }*/
        basicAuth = ""; // clear out stored Password hash
        useDigestAuth = false;
        openChannels.close();
        channelTrackingMap.clear();
    }

    public static class SupportPTZ implements Predicate<Object> {

        @Override
        public boolean test(Object o) {
            return ((OnvifCameraHandler) o).onvifCamera.supportsPTZ();
        }
    }

    private class OnvifCameraStreamHandler extends BaseCameraStreamServerHandler<OnvifCameraHandler> {
        private boolean onvifEvent;
        private boolean handlingMjpeg = false; // used to remove ctx from group when handler is removed.
        private boolean handlingSnapshotStream = false; // used to remove ctx from group when handler is removed.

        public OnvifCameraStreamHandler(OnvifCameraHandler onvifCameraHandler) {
            super(onvifCameraHandler);
        }

        @Override
        protected void handleLastHttpContent(byte[] incomingJpeg) {
            if (onvifEvent) {
                onvifCamera.eventRecieved(new String(incomingJpeg, StandardCharsets.UTF_8));
            } else { // handles the snapshots that make up mjpeg from rtsp to ffmpeg conversions.
                if (incomingJpeg.length > 1000) {
                    sendMjpegFrame(incomingJpeg, mjpegChannelGroup);
                }
            }
        }

        @Override
        protected boolean handleHttpRequest(QueryStringDecoder queryStringDecoder, ChannelHandlerContext ctx) {
            switch (queryStringDecoder.path()) {
                case "/ipcamera.jpg":
                    if (!cameraHandler.snapshotPolling && !cameraHandler.snapshotUri.equals("")) {
                        cameraHandler.sendHttpGET(cameraHandler.snapshotUri);
                    }
                    if (cameraHandler.currentSnapshot.length == 1) {
                        log.warn("ipcamera.jpg was requested but there is no jpg in ram to send.");
                        return true;
                    }
                    return true;
                case "/snapshots.mjpeg":
                    handlingSnapshotStream = true;
                    cameraHandler.startSnapshotPolling();
                    cameraHandler.setupSnapshotStreaming(true, ctx, false);
                    return true;
                case "/ipcamera.mjpeg":
                    cameraHandler.setupMjpegStreaming(true, ctx);
                    handlingMjpeg = true;
                    return true;
                case "/autofps.mjpeg":
                    handlingSnapshotStream = true;
                    cameraHandler.setupSnapshotStreaming(true, ctx, true);
                    return true;
                case "/instar":
                    InstarTypeHandler instar = new InstarTypeHandler(cameraHandler);
                    instar.alarmTriggered(queryStringDecoder.uri());
                    ctx.close();
                    return true;
            }
            return false;
        }

        @Override
        protected boolean streamServerReceivedPostHandler(HttpRequest httpRequest) {
            if ("/OnvifEvent".equals(httpRequest.uri())) {
                onvifEvent = true;
                return true;
            }
            return false;
        }

        @Override
        protected void handlerChildRemoved(ChannelHandlerContext ctx) {
            if (handlingMjpeg) {
                cameraHandler.setupMjpegStreaming(false, ctx);
            } else if (handlingSnapshotStream) {
                handlingSnapshotStream = false;
                cameraHandler.setupSnapshotStreaming(false, ctx, false);
            }
        }
    }
}

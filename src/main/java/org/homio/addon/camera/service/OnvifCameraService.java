package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.api.util.CommonUtils.MACHINE_IP_ADDRESS;

import de.onvif.soap.OnvifDeviceState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.onvif.impl.InstarBrandHandler;
import org.homio.addon.camera.onvif.impl.UnknownBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.onvif.util.MyNettyAuthHandler;
import org.homio.addon.camera.ui.UICameraActionConditional;
import org.homio.addon.camera.ui.UICameraDimmerButton;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.ObjectType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.RawType;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class OnvifCameraService extends BaseVideoService<OnvifCameraEntity> {

    @NotNull
    private static final Map<String, CameraBrandHandlerDescription> cameraBrands = new ConcurrentHashMap<>();
    // ChannelGroup is thread safe
    @NotNull
    public final ChannelGroup mjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    @Getter
    @NotNull
    private final BaseOnvifCameraBrandHandler brandHandler;
    // private GroupTracker groupTracker;
    @NotNull
    private final ChannelGroup snapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @NotNull
    private final ChannelGroup autoSnapshotMjpegChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @NotNull
    private final ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    @NotNull
    private final EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();
    @Getter
    @NotNull
    private final OnvifDeviceState onvifDeviceState;
    @NotNull
    public Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();
    public boolean useDigestAuth = false;
    @Setter
    @Getter
    public String mjpegUri = "";
    public boolean snapshotPolling = false;
    @Setter
    @Getter
    private String snapshotUri;
    private boolean streamingAutoFps = false;
    private EntityContextBGP.ThreadContext<Void> snapshotJob;
    private Bootstrap mainBootstrap;
    @NotNull
    private FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), "");
    // basicAuth MUST remain private as it holds the cameraEntity.getPassword()
    @NotNull
    private FullHttpRequest postRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("POST"), "");
    private String basicAuth = "";
    @NotNull
    private Object firstStreamedMsg = new Object();
    private boolean streamingSnapshotMjpeg = false;
    private boolean updateAutoFps = false;
    private EntityContextBGP.ThreadContext<Void> pullConfigSchedule;

    public OnvifCameraService(EntityContext entityContext, OnvifCameraEntity entity) {
        super(entity, entityContext);

        onvifDeviceState = new OnvifDeviceState(entity.getEntityID());

        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(),
            entity.getServerPort(), entity.getUser(), entity.getPassword().asString());

        onvifDeviceState.setUnreachableHandler(message -> this.disposeAndSetStatus(Status.OFFLINE, message));

        onvifDeviceState.getEventDevices().subscribe("RuleEngine/CellMotionDetector/Motion",
            (dataName, dataValue) -> motionDetected(dataValue.equals("true"), IpCameraBindingConstants.CHANNEL_CELL_MOTION_ALARM));
        onvifDeviceState.getEventDevices().subscribe("VideoSource/MotionAlarm",
            (dataName, dataValue) -> motionDetected(dataValue.equals("true"), IpCameraBindingConstants.CHANNEL_MOTION_ALARM));
        onvifDeviceState.getEventDevices().subscribe("AudioAnalytics/Audio/DetectedSound",
            (dataName, dataValue) -> audioDetected(dataValue.equals("true")));
        onvifDeviceState.getEventDevices().subscribe("RuleEngine/FieldDetector/ObjectsInside",
            (dataName, dataValue) -> motionDetected(dataValue.equals("true"), IpCameraBindingConstants.CHANNEL_FIELD_DETECTION_ALARM));
        onvifDeviceState.getEventDevices().subscribe("RuleEngine/LineDetector/Crossed",
            (dataName, dataValue) -> motionDetected(dataName.equals("ObjectId"), IpCameraBindingConstants.CHANNEL_LINE_CROSSING_ALARM));
        onvifDeviceState.getEventDevices().subscribe("RuleEngine/TamperDetector/Tamper",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_TAMPER_ALARM, OnOffType.of(dataValue.equals("true"))));
        onvifDeviceState.getEventDevices().subscribe("Device/HardwareFailure/StorageFailure",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_STORAGE_ALARM, OnOffType.of(dataValue.equals("true"))));

        onvifDeviceState.getEventDevices().subscribe(
            "VideoSource/ImageTooDark/AnalyticsService",
            "VideoSource/ImageTooDark/ImagingService",
            "VideoSource/ImageTooDark/RecordingService",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_TOO_DARK_ALARM, OnOffType.of(dataValue.equals("true"))));

        onvifDeviceState.getEventDevices().subscribe(
            "VideoSource/GlobalSceneChange/AnalyticsService",
            "VideoSource/GlobalSceneChange/ImagingService",
            "VideoSource/GlobalSceneChange/RecordingService",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_SCENE_CHANGE_ALARM, OnOffType.of(dataValue.equals("true"))));

        onvifDeviceState.getEventDevices().subscribe(
            "VideoSource/ImageTooBright/AnalyticsService",
            "VideoSource/ImageTooBright/ImagingService",
            "VideoSource/ImageTooBright/RecordingService",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_TOO_BRIGHT_ALARM, OnOffType.of(dataValue.equals("true"))));

        onvifDeviceState.getEventDevices().subscribe(
            "VideoSource/ImageTooBlurry/AnalyticsService",
            "VideoSource/ImageTooBlurry/ImagingService",
            "VideoSource/ImageTooBlurry/RecordingService",
            (dataName, dataValue) -> setAttribute(IpCameraBindingConstants.CHANNEL_TOO_BLURRY_ALARM, OnOffType.of(dataValue.equals("true"))));

        if (entity.getCameraType() != null && getCameraBrands(entityContext).containsKey(entity.getCameraType())) {
            CameraBrandHandlerDescription cameraBrandHandlerDescription = getCameraBrands(entityContext).get(entity.getCameraType());
            this.brandHandler = cameraBrandHandlerDescription.newInstance(this);
        } else {
            this.brandHandler = new UnknownBrandHandler(this);
        }
    }

    public static Map<String, CameraBrandHandlerDescription> getCameraBrands(EntityContext entityContext) {
        if (cameraBrands.isEmpty()) {
            for (Class<? extends BaseOnvifCameraBrandHandler> brandHandlerClass :
                entityContext.getClassesWithParent(BaseOnvifCameraBrandHandler.class)) {
                cameraBrands.put(brandHandlerClass.getSimpleName(), new CameraBrandHandlerDescription(brandHandlerClass));
            }
        }
        return cameraBrands;
    }

    @Override
    protected void initialize() {
        onvifDeviceState.updateParameters(getEntity().getIp(), getEntity().getOnvifPort(),
            getEntity().getServerPort(), getEntity().getUser(), getEntity().getPassword().asString());
        // change camera name if possible
        tryChangeCameraName();
    }

    public VideoPlaybackStorage getVideoPlaybackStorage() {
        return (VideoPlaybackStorage) brandHandler;
    }

    // false clears the stored user/pass hash, true creates the hash
    public boolean setBasicAuth(boolean useBasic) {
        if (!useBasic) {
            log.debug("[{}]: Clearing out the stored BASIC auth now.", getEntityID());
            basicAuth = "";
            return false;
        } else if (!basicAuth.isEmpty()) {
            // due to camera may have been sent multiple requests before the auth was set, this may trigger falsely.
            log.warn("[{}]: Camera is reporting your username and/or password is wrong.", getEntityID());
            return false;
        }
        OnvifCameraEntity entity = getEntity();
        if (!entity.getUser().isEmpty() && !entity.getPassword().isEmpty()) {
            String authString = entity.getUser() + ":" + entity.getPassword().asString();
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
            disposeAndSetStatus(Status.ERROR, "Camera is asking for Basic Auth when you have not provided a username and/or password.");
        }
        return false;
    }

    public void sendHttpPUT(String httpRequestURL, FullHttpRequest request) {
        putRequestWithBody = request; // use Global so the authhandler can use it when resent with DIGEST.
        sendHttpRequest("PUT", httpRequestURL, null);
    }

    public void sendHttpGET(String httpRequestURL) {
        sendHttpRequest("GET", httpRequestURL, null);
    }

    public void sendHttpPOST(String httpRequestURL, FullHttpRequest request) {
        postRequestWithBody = request; // use Global so the authhandler can use it when resent with DIGEST.
        sendHttpRequest("POST", httpRequestURL, null);
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
        OnvifCameraEntity entity = getEntity();
        int port = getPortFromShortenedUrl(httpRequestURLFull, entity);
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
                    socketChannel.pipeline().addLast(IpCameraBindingConstants.AUTH_HANDLER,
                        new MyNettyAuthHandler(entity.getUser(),
                            entity.getPassword().asString(), OnvifCameraService.this));
                    socketChannel.pipeline().addLast(IpCameraBindingConstants.COMMON_HANDLER, new CommonCameraHandler());

                    String handlerName = OnvifCameraService.getCameraBrands(entityContext).get(entity.getCameraType()).getHandlerName();
                    socketChannel.pipeline().addLast(handlerName, brandHandler.asBootstrapHandler());
                }
            });
        }

        FullHttpRequest request;
        if ("GET".equals(httpMethod) || (useDigestAuth && digestString == null)) {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod(httpMethod), httpRequestURL);
            request.headers().set(HttpHeaderNames.HOST, entity.getIp() + ":" + port);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else if ("PUT".equals(httpMethod)) {
            request = putRequestWithBody;
        } else {
            request = postRequestWithBody;
        }

        if (!basicAuth.isEmpty()) {
            if (useDigestAuth) {
                log.warn("[{}]: Camera at IP:{} had both Basic and Digest set to be used", getEntityID(), entity.getIp());
                setBasicAuth(false);
            } else {
                request.headers().set(HttpHeaderNames.AUTHORIZATION, "Basic " + basicAuth);
            }
        }

        if (useDigestAuth) {
            if (digestString != null) {
                request.headers().set(HttpHeaderNames.AUTHORIZATION, "Digest " + digestString);
            }
        }

        mainBootstrap.connect(new InetSocketAddress(entity.getIp(), port))
                     .addListener((ChannelFutureListener) future -> {
                         if (future == null) {
                             return;
                         }
                         if (future.isDone() && future.isSuccess()) {
                             Channel ch = future.channel();
                             openChannels.add(ch);
                             log.debug("[{}]: Sending camera: {}: http://{}:{}{}", getEntityID(), httpMethod,
                                 entity.getIp(), port, httpRequestURL);
                             channelTrackingMap.put(httpRequestURL, new ChannelTracking(ch, httpRequestURL));

                             CommonCameraHandler commonHandler = (CommonCameraHandler) ch.pipeline().get(IpCameraBindingConstants.COMMON_HANDLER);
                             commonHandler.setURL(httpRequestURLFull);
                             MyNettyAuthHandler authHandler = (MyNettyAuthHandler) ch.pipeline().get(IpCameraBindingConstants.AUTH_HANDLER);
                             authHandler.setURL(httpMethod, httpRequestURL);

                             brandHandler.handleSetURL(ch.pipeline(), httpRequestURL);
                             ch.writeAndFlush(request);
                         } else { // an error occurred
                             log.warn("[{}]: Error handle camera: <{}>. Error: <{}>", getEntityID(), entity,
                                 CommonUtils.getErrorMessage(future.cause()));

                        /*if (!this.restartingBgp) {
                            log.error("Error in camera <{}> boostrap: <{}>", cameraEntity.getTitle(),
                                    CommonUtils.getErrorMessage(future.cause()));
                            this.retryCount *= 2;
                            this.restartingBgp = true;
                            log.info("Try restart camera <{}> in <{}> sec", cameraEntity.getTitle(), this.retryCount);
                            entityContext.bgp().run("Restart camera " + cameraEntity.getTitle(), retryCount * 1000, () -> {
                                boolean restarted = restart("Connection Timeout: Check your IP and PORT are correct and the camera can be reached
                                .", null, false);
                                if (restarted) {
                                    this.retryCount = 1;
                                }
                                this.restartingBgp = false;
                            }, false);
                        }*/
                         }
                     });
    }

    public void setupSnapshotStreaming(boolean stream, ChannelHandlerContext ctx, boolean auto) {
        if (stream) {
            sendMjpegFirstPacket(ctx);
            if (auto) {
                autoSnapshotMjpegChannelGroup.add(ctx.channel());
                lockCurrentSnapshot.lock();
                try {
                    sendMjpegFrame(getLatestSnapshot(), autoSnapshotMjpegChannelGroup);
                    // iOS uses a FIFO? and needs two frames to display a pic
                    sendMjpegFrame(getLatestSnapshot(), autoSnapshotMjpegChannelGroup);
                } finally {
                    lockCurrentSnapshot.unlock();
                }
                streamingAutoFps = true;
            } else {
                snapshotMjpegChannelGroup.add(ctx.channel());
                lockCurrentSnapshot.lock();
                try {
                    sendMjpegFrame(getLatestSnapshot(), snapshotMjpegChannelGroup);
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
                log.info("[{}]: All snapshots.mjpeg streams have stopped.", getEntityID());
            } else if (streamingAutoFps && autoSnapshotMjpegChannelGroup.isEmpty()) {
                streamingAutoFps = false;
                stopSnapshotPolling();
                log.info("[{}]: All autofps.mjpeg streams have stopped.", getEntityID());
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
            } else if (getFfmpegMjpeg() != null) {// not first stream and we will use ffmpeg
                sendMjpegFirstPacket(ctx);
                mjpegChannelGroup.add(ctx.channel());
            } else {// not first stream and camera supplies the mjpeg source.
                ctx.channel().writeAndFlush(firstStreamedMsg);
                mjpegChannelGroup.add(ctx.channel());
            }
        } else {
            mjpegChannelGroup.remove(ctx.channel());
            if (mjpegChannelGroup.isEmpty()) {
                log.info("[{}]: All ipcamera.mjpeg streams have stopped.", getEntityID());
                if (mjpegUri.equals("ffmpeg") || mjpegUri.isEmpty()) {
                    FFMPEG localMjpeg = getFfmpegMjpeg();
                    if (localMjpeg != null) {
                        localMjpeg.stopConverting();
                    }
                } else {
                    closeChannel(getTinyUrl(mjpegUri));
                }
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
        response.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().add(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
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
    public String getFFMPEGInputOptions(@Nullable String profile) {
        String inputOptions = getEntity().getFfmpegInputOptions();
        String rtspUri = getRtspUri(profile);
        if (rtspUri.isEmpty()) {
            log.warn("[{}]: The camera tried to use a FFmpeg feature when no valid input for FFmpeg is provided.", getEntityID());
            return null;
        }
        if (rtspUri.toLowerCase().contains("rtsp")) {
            if (inputOptions.isEmpty()) {
                inputOptions = "-rtsp_transport tcp";
            }
        }
        if (!inputOptions.contains("timeout")) {
            inputOptions += " -timeout " + TimeUnit.SECONDS.toMicros(10);
        }
        return inputOptions;
    }

    @Override
    public void motionDetected(boolean on, String key) {
        super.motionDetected(on, key);
        if (on) {
            if (streamingAutoFps) {
                startSnapshotPolling();
            }
        } else {
            if (streamingAutoFps) {
                stopSnapshotPolling();
            }
        }
    }

    @Override
    public void processSnapshot(byte[] incomingSnapshot) {
        super.processSnapshot(incomingSnapshot);

        if (streamingSnapshotMjpeg) {
            sendMjpegFrame(incomingSnapshot, snapshotMjpegChannelGroup);
        }
        if (streamingAutoFps) {
            if (isMotionDetected()) {
                sendMjpegFrame(incomingSnapshot, autoSnapshotMjpegChannelGroup);
            } else if (updateAutoFps) {
                // only happens every 8 seconds as some browsers need a frame that often to keep stream alive.
                sendMjpegFrame(incomingSnapshot, autoSnapshotMjpegChannelGroup);
                updateAutoFps = false;
            }
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

    @Override
    public void startSnapshot() {
        if (!isEmpty(snapshotUri)) {
            sendHttpGET(snapshotUri);// Allows this to change Image FPS on demand
        } else {
            super.startSnapshot();
        }
    }

    @Override
    public void testVideoOnline() {
        getOnvifDeviceState().checkForErrors();
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_PAN)
    public DecimalType getPan() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentPanPercentage()));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_PAN, order = 3, icon = "fas fa-expand-arrows-alt", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "LEFT", icon = "fas fa-caret-left")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "RIGHT", icon = "fas fa-caret-right")
    public void setPan(String command) {
        if ("LEFT".equals(command) || "RIGHT".equals(command)) {
            if ("LEFT".equals(command)) {
                onvifDeviceState.getPtzDevices().moveLeft(getEntity().isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveRight(getEntity().isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        } else {
            onvifDeviceState.getPtzDevices().setAbsolutePan(Float.parseFloat(command));
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_TILT)
    public DecimalType getTilt() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentTiltPercentage()));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TILT, order = 5, icon = "fas fa-sort", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "UP", icon = "fas fa-caret-up")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "DOWN", icon = "fas fa-caret-down")
    public void setTilt(String command) {
        if ("UP".equals(command) || "DOWN".equals(command)) {
            if ("UP".equals(command)) {
                onvifDeviceState.getPtzDevices().moveUp(getEntity().isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveDown(getEntity().isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        } else {
            onvifDeviceState.getPtzDevices().setAbsoluteTilt(Float.parseFloat(command));
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ZOOM)
    public DecimalType getZoom() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentZoomPercentage()));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ZOOM, order = 7, icon = "fas fa-search-plus", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "IN", icon = "fas fa-search-plus")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "OUT", icon = "fas fa-search-minus")
    public void setZoom(String command) {
        if ("IN".equals(command) || "OUT".equals(command)) {
            if ("IN".equals(command)) {
                onvifDeviceState.getPtzDevices().moveIn(getEntity().isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveOut(getEntity().isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        }
        onvifDeviceState.getPtzDevices().setAbsoluteZoom(Float.valueOf(command));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_GOTO_PRESET)
    public DecimalType getGotoPreset() {
        return new DecimalType(0);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_GOTO_PRESET, order = 30, icon = "fas fa-location-arrow", min = 1, max = 25, selectReplacer =
        "Preset %0  ")
    @UICameraActionConditional(SupportPTZ.class)
    public void gotoPreset(int preset) {
        onvifDeviceState.getPtzDevices().gotoPreset(preset);
    }

    public boolean streamIsStopped(String url) {
        ChannelTracking channelTracking = channelTrackingMap.get(url);
        if (channelTracking != null) {
            return !channelTracking.getChannel().isActive(); // stream is running.
        }
        return true; // Stream stopped or never started.
    }

    public String getRtspUri(@Nullable String profile) {
        return defaultIfEmpty(getEntity().getFfmpegInput(), onvifDeviceState.getMediaDevices().getRTSPStreamUri(profile));
    }

    @Override
    public RawType recordImageSync(String profile) {
        if (isEmpty(snapshotUri)) {
            return super.recordImageSync(profile);
        }
        String snapshotUri = onvifDeviceState.getMediaDevices().getSnapshotUri(profile);
        OnvifCameraEntity entity = getEntity();
        return new RawType(Curl.download(snapshotUri, entity.getUser(), entity.getPassword().asString()));
    }

    @Override
    protected void updateNotificationBlock() {
        CameraEntrypoint.updateCamera(entityContext, getEntity(),
            () -> {
                val brand = getCameraBrands(entityContext).get(getEntity().getCameraType());
                return getEntity().getIp() + ":" + getEntity().getOnvifPort() + " " + brand.getName();
            }, new Icon("fas fa-wifi", "#0E578F"),
            actionBuilder -> actionBuilder.addButton("RESTART", new Icon("fas fa-power-off"),
                (entityContext, params) -> {
                    String response = onvifDeviceState.getInitialDevices().reboot();
                    return ActionResponseModel.showSuccess(response);
                }));
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
     * This method should never run under normal use, if there is a bug in a camera or binding it may be possible to open large amounts of channels. This may
     * help to keep it under control and WARN the user every 8 seconds this is still occurring.
     */
    void cleanChannels() {
        for (Channel channel : openChannels) {
            boolean oldChannel = true;
            for (ChannelTracking channelTracking : channelTrackingMap.values()) {
                if (!channelTracking.getChannel().isOpen() && channelTracking.getReply().isEmpty()) {
                    channelTrackingMap.remove(channelTracking.getRequestUrl());
                }
                if (channelTracking.getChannel() == channel) {
                    log.debug("[{}]: Open channel to camera is used for URL:{}", getEntityID(), channelTracking.getRequestUrl());
                    oldChannel = false;
                }
            }
            if (oldChannel) {
                channel.close();
            }
        }
    }

    void snapshotRunnable() {
        sendHttpGET(snapshotUri);
    }

    @Override
    protected void streamServerStarted() {
        if (getEntity().getCameraType().equals("instar")) {
            log.info("[{}]: Setting up the Alarm Server settings in the camera now", getEntityID());
            sendHttpGET("/param.cgi?cmd=setmdalarm&-aname=server2&-switch=on&-interval=1&cmd=setalarmserverattr&-as_index=3&-as_server="
                + MACHINE_IP_ADDRESS + "&-as_port=" + serverPort + "&-as_path=/instar&-as_queryattr1=&-as_queryval1=&-as_queryattr2" +
                "=&-as_queryval2=&-as_queryattr3=&-as_queryval3=&-as_activequery=1&-as_auth=0&-as_query1=0&-as_query2=0&-as_query3=0");
        }
    }

    @Override
    protected BaseVideoStreamServerHandler createVideoStreamServerHandler() {
        return new OnvifCameraStreamHandler(this);
    }

    @Override
    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {
        brandHandler.assembleActions(uiInputBuilder);
    }

    @Override
    protected void pollingVideoConnection() {
        startSnapshot();
    }

    @Override
    protected void pollVideoRunnable() {
        super.pollVideoRunnable();

        // Snapshot should be first to keep consistent time between shots
        if (streamingAutoFps) {
            updateAutoFps = true;
            if (!snapshotPolling && StringUtils.isEmpty(snapshotUri)) {
                // Dont need to poll if creating from RTSP stream with FFmpeg or we are polling at full rate already.
                sendHttpGET(snapshotUri);
            }
        } else if (!isEmpty(snapshotUri) && !snapshotPolling) {// we need to check camera is still online.
            sendHttpGET(snapshotUri);
        }
        // what needs to be done every poll//
        brandHandler.pollCameraRunnable();

        if (openChannels.size() > 18) {
            log.debug("[{}]: There are {} open Channels being tracked.", getEntityID(), openChannels.size());
            cleanChannels();
        }
    }

    @Override
    protected void initialize0() {
        OnvifCameraEntity entity = getEntity();

        this.onvifDeviceState.initFully(entity.getOnvifMediaProfile(), brandHandler.isSupportOnvifEvents());
        super.initialize0();

        setAttribute("PROFILES", new ObjectType(onvifDeviceState.getProfiles()));
        snapshotUri = getCorrectUrlFormat(entity.getSnapshotUrl());
        mjpegUri = getCorrectUrlFormat(entity.getMjpegUrl());

        brandHandler.initialize(entityContext);

        pullConfigSchedule = entityContext.bgp().builder("Camera " + entity.getEntityID() + " run per minute")
                                          .delay(Duration.ofSeconds(30)).interval(Duration.ofSeconds(60)).execute(() -> {
                try {
                    onvifDeviceState.runOncePerMinute();
                    brandHandler.runOncePerMinute(entityContext);
                } catch (Exception ex) {
                    log.error("[{}]: Error during execute onvif service: {}", entityID, CommonUtils.getErrorMessage(ex));
                    if (ex.getCause() instanceof ConnectException) {
                        entity.setStatusError("Connection exception");
                    }
                }
            });

        if (("ffmpeg".equals(snapshotUri) || isEmpty(snapshotUri)) && isEmpty(getRtspUri(null))) {
            throw new RuntimeException("Camera unable to find valid Snapshot and/or RTSP URL.");
        }

        if (snapshotUri.equals("ffmpeg")) {
            log.warn("[{}]: Camera <{}> has no snapshot url. Will use your CPU and FFmpeg to create snapshots from the cameras RTSP.",
                getEntityID(), entity.getTitle());
            snapshotUri = "";
        }
        setAttribute("SNAPSHOT_URI", new StringType(snapshotUri));
    }

    @Override
    protected void dispose0() {
        super.dispose0();
        snapshotPolling = false;
        onvifDeviceState.dispose();

        if (pullConfigSchedule != null) {
            pullConfigSchedule.cancel();
        }

        if (snapshotJob != null) {
            snapshotJob.cancel();
        }
        /*if (this.onvifPollCameraEach8Sec != null) {
            this.onvifPollCameraEach8Sec.cancel();
        }*/
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

    @Override
    protected void setAudioAlarmThreshold(int audioThreshold) {
        ((BrandCameraHasAudioAlarm) brandHandler).setAudioAlarmThreshold(audioThreshold);
    }

    @Override
    protected void setMotionAlarmThreshold(int motionThreshold) {
        ((BrandCameraHasMotionAlarm) brandHandler).setMotionAlarmThreshold(motionThreshold);
    }

    @Override
    protected boolean isAudioAlarmHandlesByVideo() {
        return brandHandler instanceof BrandCameraHasAudioAlarm;
    }

    @Override
    protected boolean isMotionAlarmHandlesByVideo() {
        return brandHandler instanceof BrandCameraHasMotionAlarm;
    }

    @Override
    protected long getEntityHashCode(EntityService entity) {
        return getEntity().getDeepHashCode();
    }

    private void tryChangeCameraName() {
        if (onvifDeviceState.getInitialDevices() != null && this.isHandlerInitialized()) {
            try {
                if (!Objects.equals(onvifDeviceState.getInitialDevices().getName(), getEntity().getName())) {
                    onvifDeviceState.getInitialDevices().setName(getEntity().getName());
                }
            } catch (Exception ex) {
                log.error("[{}]: Unable to change onvif camera name: {}", getEntityID(), CommonUtils.getErrorMessage(ex));
            }
        }
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
            disposeAndSetStatus(Status.ERROR, "A non valid URL has been given to the binding, check they work in a browser.");
        }
        return temp;
    }

    private int getPortFromShortenedUrl(String httpRequestURL, OnvifCameraEntity entity) {
        if (httpRequestURL.startsWith(":")) {
            int end = httpRequestURL.indexOf("/");
            return Integer.parseInt(httpRequestURL.substring(1, end));
        }
        return entity.getRestPort();
    }

    private void stopSnapshotPolling() {
        if (!streamingSnapshotMjpeg) {
            snapshotPolling = false;
            if (snapshotJob != null) {
                snapshotJob.cancel();
            }
        }
    }

    private void startSnapshotPolling() {
        if (snapshotPolling || StringUtils.isEmpty(snapshotUri)) {
            return; // Already polling or creating with FFmpeg from RTSP
        }
        if (streamingSnapshotMjpeg || streamingAutoFps) {
            snapshotPolling = true;
            OnvifCameraEntity entity = getEntity();
            snapshotJob = entityContext.bgp().builder(entity.getTitle() + " SnapshotJob").delay(Duration.ofMillis(200))
                                       .interval(Duration.ofSeconds(entity.getSnapshotPollInterval())).execute(() -> sendHttpGET(snapshotUri));
        }
    }

    public static class SupportPTZ implements Predicate<Object> {

        @Override
        public boolean test(Object o) {
            return ((OnvifCameraService) o).onvifDeviceState.getPtzDevices().supportPTZ();
        }
    }

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
                                // Some cameras use first letter uppercase and others don't.
                                switch (name.toLowerCase()) { // Possible localization issues doing this
                                    case "content-type" -> contentType = response.headers().getAsString(name);
                                    case "content-length" -> bytesToReceive = Integer.parseInt(response.headers().getAsString(name));
                                    case "connection" -> {
                                        if (response.headers().getAsString(name).contains("keep-alive")) {
                                            closeConnection = false;
                                        }
                                    }
                                    case "transfer-encoding" -> {
                                        if (response.headers().getAsString(name).contains("chunked")) {
                                            isChunked = true;
                                        }
                                    }
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
                                    log.debug("[{}]: Camera has no Content-Length header, we have to guess how much RAM.", getEntityID());
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
                                bringVideoOnline();
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
                                log.debug("[{}]: Reply is chunked.", getEntityID());
                                reply = incomingMessage;
                                super.channelRead(ctx, reply);
                            }
                        }
                    }
                } else { // msg is not HttpContent
                    // Foscam cameras need this
                    if (!contentType.contains("image/jp") && bytesAlreadyReceived != 0) {
                        reply = incomingMessage;
                        log.debug("[{}]: Packet back from camera is {}", getEntityID(), incomingMessage);
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
                log.debug("[{}]: Camera sent {} bytes when the content-length header was {}.", getEntityID(),
                    bytesAlreadyReceived, bytesToReceive);
            } else {
                log.warn("[{}]: !!!! Camera possibly closed the channel on the binding, cause reported is: {}",
                    getEntityID(), cause.getMessage());
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
                    String urlToKeepOpen = brandHandler.getUrlToKeepOpenForIdleStateEvent();
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

    private class OnvifCameraStreamHandler extends BaseVideoStreamServerHandler<OnvifCameraService> {

        private boolean onvifEvent;
        private boolean handlingMjpeg = false; // used to remove ctx from group when handler is removed.
        private boolean handlingSnapshotStream = false; // used to remove ctx from group when handler is removed.

        public OnvifCameraStreamHandler(OnvifCameraService service) {
            super(service);
        }

        @Override
        protected void handleLastHttpContent(byte[] bytes) {
            if (onvifEvent) {
                onvifDeviceState.getEventDevices().fireEvent(new String(bytes, StandardCharsets.UTF_8));
            } else { // handles the snapshots that make up mjpeg from rtsp to ffmpeg conversions.
                if (bytes.length > 1000) {
                    sendMjpegFrame(bytes, mjpegChannelGroup);
                }
            }
        }

        @Override
        protected void handleChildrenHttpRequest(QueryStringDecoder queryStringDecoder, ChannelHandlerContext ctx) {
            switch (queryStringDecoder.path()) {
                case "/ipvideo.jpg" -> {
                    if (!snapshotPolling && !snapshotUri.equals("")) {
                        sendHttpGET(snapshotUri);
                    }
                    if (getLatestSnapshot().length == 1) {
                        log.warn("[{}]: ipvideo.jpg was requested but there is no jpg in ram to send.", getEntityID());
                    }
                }
                case "/snapshots.mjpeg" -> {
                    handlingSnapshotStream = true;
                    startSnapshotPolling();
                    setupSnapshotStreaming(true, ctx, false);
                }
                case "/ipvideo.mjpeg" -> {
                    setupMjpegStreaming(true, ctx);
                    handlingMjpeg = true;
                }
                case "/autofps.mjpeg" -> {
                    handlingSnapshotStream = true;
                    setupSnapshotStreaming(true, ctx, true);
                }
                case "/instar" -> {
                    InstarBrandHandler instar = new InstarBrandHandler(OnvifCameraService.this);
                    instar.alarmTriggered(queryStringDecoder.uri());
                    ctx.close();
                }
            }
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
                setupMjpegStreaming(false, ctx);
            } else if (handlingSnapshotStream) {
                handlingSnapshotStream = false;
                setupSnapshotStreaming(false, ctx, false);
            }
        }
    }
}

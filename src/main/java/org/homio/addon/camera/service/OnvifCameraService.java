package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.addon.camera.CameraController.camerasOpenStreams;
import static org.homio.api.util.CommonUtils.getErrorMessage;

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
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
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
import java.time.Duration;
import java.time.Instant;
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
import org.homio.addon.camera.CameraController.OpenStreamsContainer;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.onvif.impl.UnknownBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.onvif.util.MyNettyAuthHandler;
import org.homio.addon.camera.ui.UICameraActionConditional;
import org.homio.addon.camera.ui.UICameraDimmerButton;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.state.DecimalType;
import org.homio.api.state.ObjectType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.RawType;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class OnvifCameraService extends BaseVideoService<OnvifCameraEntity, OnvifCameraService> {

    private static @NotNull final Map<String, CameraBrandHandlerDescription> cameraBrands = new ConcurrentHashMap<>();
    private final @NotNull ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Getter
    private final @NotNull BaseOnvifCameraBrandHandler brandHandler;

    private final @NotNull EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup(1);
    @Getter
    private final @NotNull OnvifDeviceState onvifDeviceState;
    public @NotNull Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();
    public boolean useDigestAuth = false;
    @Setter
    @Getter
    public String mjpegUri = "";
    public String mjpegContentType = "";
    @Setter
    @Getter
    private String snapshotUri;
    private Bootstrap mainBootstrap;
    private @NotNull FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), "");
    // basicAuth MUST remain private as it holds the cameraEntity.getPassword()
    private @NotNull FullHttpRequest postRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("POST"), "");
    private String basicAuth = "";

    //
    public boolean updateAutoFps = false;
    public boolean ffmpegSnapshotGeneration = false;
    public boolean snapshotPolling = false;
    public boolean streamingSnapshotMjpeg = false;
    public boolean streamingAutoFps = false;

    //
    private EntityContextBGP.ThreadContext<Void> snapshotJob;
    private EntityContextBGP.ThreadContext<Void> pullConfigJob;

    public OnvifCameraService(EntityContext entityContext, OnvifCameraEntity entity) {
        super(entity, entityContext);
        camerasOpenStreams.putIfAbsent(entityID, new OpenStreamsContainer());

        onvifDeviceState = new OnvifDeviceState(entity.getEntityID());
        onvifDeviceState.setUpdateListener(() -> entityContext.ui().updateItem(entity));

        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(), entity.getUser(), entity.getPassword().asString());

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
    public String getFFMPEGInputOptions(@Nullable String profile) {
        String inputOptions = entity.getFfmpegInputOptions();
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
                        new MyNettyAuthHandler(entity, OnvifCameraService.this));
                    socketChannel.pipeline().addLast(IpCameraBindingConstants.COMMON_HANDLER, new CommonCameraHandler());

                    socketChannel.pipeline().addLast(brandHandler.getClass().getSimpleName(), brandHandler.asBootstrapHandler());
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
                             bringCameraOnline();
                             log.debug("[{}]: Sending camera: {}: http://{}:{}{}", getEntityID(), httpMethod,
                                 entity.getIp(), port, httpRequestURL);
                             channelTrackingMap.put(httpRequestURL, new ChannelTracking(ch, httpRequestURL));

                             CommonCameraHandler commonHandler = (CommonCameraHandler) ch.pipeline().get(IpCameraBindingConstants.COMMON_HANDLER);
                             commonHandler.setRequestUrl(httpRequestURLFull);
                             MyNettyAuthHandler authHandler = (MyNettyAuthHandler) ch.pipeline().get(IpCameraBindingConstants.AUTH_HANDLER);
                             authHandler.setURL(httpMethod, httpRequestURL);

                             brandHandler.handleSetURL(ch.pipeline(), httpRequestURL);
                             ch.writeAndFlush(request);
                         } else { // an error occurred
                             log.warn("[{}]: Error handle camera: <{}>. Error: <{}>", getEntityID(), entity, getErrorMessage(future.cause()));
                             entity.setStatusError("Connection Timeout");
                         }
                     });
    }

    public void storeHttpReply(String url, String content) {
        ChannelTracking channelTracking = channelTrackingMap.get(url);
        if (channelTracking != null) {
            channelTracking.setReply(content);
        }
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_PAN, order = 3, icon = "fas fa-expand-arrows-alt", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "LEFT", icon = "fas fa-caret-left")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "RIGHT", icon = "fas fa-caret-right")
    public void setPan(String command) {
        if ("LEFT".equals(command) || "RIGHT".equals(command)) {
            if ("LEFT".equals(command)) {
                onvifDeviceState.getPtzDevices().moveLeft(entity.isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveRight(entity.isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        } else {
            onvifDeviceState.getPtzDevices().setAbsolutePan(Float.parseFloat(command));
        }
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

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TILT, order = 5, icon = "fas fa-sort", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "UP", icon = "fas fa-caret-up")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "DOWN", icon = "fas fa-caret-down")
    public void setTilt(String command) {
        if ("UP".equals(command) || "DOWN".equals(command)) {
            if ("UP".equals(command)) {
                onvifDeviceState.getPtzDevices().moveUp(entity.isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveDown(entity.isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        } else {
            onvifDeviceState.getPtzDevices().setAbsoluteTilt(Float.parseFloat(command));
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_TILT)
    public DecimalType getTilt() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentTiltPercentage()));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ZOOM, order = 7, icon = "fas fa-search-plus", type = UIVideoAction.ActionType.Dimmer)
    @UICameraActionConditional(SupportPTZ.class)
    @UICameraDimmerButton(name = "IN", icon = "fas fa-search-plus")
    @UICameraDimmerButton(name = "OFF", icon = "fas fa-power-off")
    @UICameraDimmerButton(name = "OUT", icon = "fas fa-search-minus")
    public void setZoom(String command) {
        if ("IN".equals(command) || "OUT".equals(command)) {
            if ("IN".equals(command)) {
                onvifDeviceState.getPtzDevices().moveIn(entity.isPtzContinuous());
            } else {
                onvifDeviceState.getPtzDevices().moveOut(entity.isPtzContinuous());
            }
        } else if ("OFF".equals(command)) {
            onvifDeviceState.getPtzDevices().stopMove();
        }
        onvifDeviceState.getPtzDevices().setAbsoluteZoom(Float.valueOf(command));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ZOOM)
    public DecimalType getZoom() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentZoomPercentage()));
    }

    public String getRtspUri(@Nullable String profile) {
        return defaultIfEmpty(entity.getFfmpegInput(), onvifDeviceState.getMediaDevices().getRTSPStreamUri(profile));
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

    public byte[] getSnapshot() {
        if (!entity.getStatus().isOnline()) {
            // Single gray pixel JPG to keep streams open when the camera goes offline so they dont stop.
            return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46,
                0x00, 0x01, 0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00, (byte) 0xff, (byte) 0xdb, 0x00, 0x43,
                0x00, 0x03, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03, 0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x04,
                0x06, 0x04, 0x04, 0x04, 0x04, 0x04, 0x08, 0x06, 0x06, 0x05, 0x06, 0x09, 0x08, 0x0a, 0x0a, 0x09,
                0x08, 0x09, 0x09, 0x0a, 0x0c, 0x0f, 0x0c, 0x0a, 0x0b, 0x0e, 0x0b, 0x09, 0x09, 0x0d, 0x11, 0x0d,
                0x0e, 0x0f, 0x10, 0x10, 0x11, 0x10, 0x0a, 0x0c, 0x12, 0x13, 0x12, 0x10, 0x13, 0x0f, 0x10, 0x10,
                0x10, (byte) 0xff, (byte) 0xc9, 0x00, 0x0b, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00,
                (byte) 0xff, (byte) 0xcc, 0x00, 0x06, 0x00, 0x10, 0x10, 0x05, (byte) 0xff, (byte) 0xda, 0x00, 0x08,
                0x01, 0x01, 0x00, 0x00, 0x3f, 0x00, (byte) 0xd2, (byte) 0xcf, 0x20, (byte) 0xff, (byte) 0xd9};
        }
        // Most cameras will return a 503 busy error if snapshot is faster than 1 second
        long lastUpdatedMs = Duration.between(lastSnapshotRequest, Instant.now()).toMillis();
        if (!snapshotPolling && !ffmpegSnapshotGeneration && lastUpdatedMs >= entity.getPollTime()) {
            updateSnapshot();
        }
        lockCurrentSnapshot.lock();
        try {
            return getLatestSnapshot();
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    public void openCamerasStream() {
        if (mjpegUri.isEmpty() || "ffmpeg".equals(mjpegUri)) {
            ffmpegMjpeg.startConverting();
        } else {
            closeChannel(getTinyUrl(mjpegUri));
            // Dahua cameras crash if you refresh (close and open) the stream without this delay.
            mainEventLoopGroup.schedule(() -> sendHttpGET(mjpegUri), 300, TimeUnit.MILLISECONDS);
        }
    }

    private void updateSnapshot() {
        lastSnapshotRequest = Instant.now();
        mainEventLoopGroup.schedule(() -> sendHttpGET(snapshotUri), 0, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void initialize() {
        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(),
            entity.getUser(), entity.getPassword().asString());
        super.initialize();
        // change camera name if possible
        tryChangeCameraName();
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
                val brand = getCameraBrands(entityContext).get(entity.getCameraType());
                return entity.getIp() + ":" + entity.getOnvifPort() + " " + brand.getName();
            }, new Icon("fas fa-wifi", "#0E578F"),
            actionBuilder -> actionBuilder.addButton("RESTART", new Icon("fas fa-power-off"),
                (entityContext, params) -> {
                    String response = onvifDeviceState.getInitialDevices().reboot();
                    return ActionResponseModel.showSuccess(response);
                }));
    }

    public void closeChannel(String url) {
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

    @Override
    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {
        brandHandler.assembleActions(uiInputBuilder);
    }

    @Override
    protected void pollingVideoConnection() {
        startSnapshot();
    }

    @Override
    protected void pollCameraRunnable() {
        super.pollCameraRunnable();

        // Snapshot should be first to keep consistent time between shots
        if (streamingAutoFps) {
            updateAutoFps = true;
            if (!snapshotPolling && isEmpty(snapshotUri)) {
                // Don't need to poll if creating from RTSP stream with FFmpeg or we are polling at full rate already.
                sendHttpGET(snapshotUri);
            }
        } else if (!isEmpty(snapshotUri) && !snapshotPolling) {// we need to check camera is still online.
            sendHttpGET(snapshotUri);
        }
        // what needs to be done every poll//
        brandHandler.pollCameraRunnable();

        if (openChannels.size() > 10) {
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

        pullConfigJob = entityContext.bgp().builder("Camera " + entity.getEntityID() + " run per minute")
                                     .delay(Duration.ofSeconds(30)).interval(Duration.ofSeconds(60)).execute(() -> {
                try {
                    onvifDeviceState.runOncePerMinute();
                    brandHandler.runOncePerMinute(entityContext);
                } catch (Exception ex) {
                    log.error("[{}]: Error during execute onvif service: {}", entityID, getErrorMessage(ex));
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
    protected void tryConnecting() {
        onvifDeviceState.getInitialDevices().getDate();
        super.tryConnecting();
    }

    @Override
    protected void dispose0() {
        super.dispose0();
        snapshotPolling = false;
        onvifDeviceState.dispose();

        if (pullConfigJob != null) {
            pullConfigJob.cancel();
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
    protected long getEntityHashCode(OnvifCameraEntity entity) {
        return entity.getDeepHashCode();
    }

    private void tryChangeCameraName() {
        onvifDeviceState.getInitialDevices();
        if (isHandlerInitialized()) {
            try {
                if (!Objects.equals(onvifDeviceState.getInitialDevices().getName(), entity.getName())) {
                    onvifDeviceState.getInitialDevices().setName(entity.getName());
                }
            } catch (Exception ex) {
                log.error("[{}]: Unable to change onvif camera name: {}", getEntityID(), getErrorMessage(ex));
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

    public void stopSnapshotPolling() {
        if (!streamingSnapshotMjpeg) {
            snapshotPolling = false;
            if (snapshotJob != null) {
                snapshotJob.cancel();
            }
        }
    }

    public void startSnapshotPolling() {
        if (snapshotPolling || isEmpty(snapshotUri)) {
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
        private String boundary = "";
        private @Setter String requestUrl = "";

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg == null || ctx == null) {
                return;
            }
            try {
                if (msg instanceof HttpResponse response) {
                    if (response.status().code() == 200) {
                        if (!response.headers().isEmpty()) {
                            for (String name : response.headers().names()) {
                                // Some cameras use first letter uppercase and others don't.
                                switch (name.toLowerCase()) { // Possible localization issues doing this
                                    case "content-type" -> contentType = response.headers().getAsString(name);
                                    case "content-length" -> bytesToReceive = Integer.parseInt(response.headers().getAsString(name));
                                }
                            }
                            if (contentType.contains("multipart")) {
                                boundary = Helper.searchString(contentType, "boundary=");
                                if (mjpegUri.equals(requestUrl)) {
                                    if (msg instanceof HttpMessage) {
                                        // very start of stream only
                                        mjpegContentType = contentType;
                                        camerasOpenStreams.get(entityID).openStreams.updateContentType(contentType, boundary);
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
                    } else {
                        // Non 200 OK replies are logged and handled in pipeline by MyNettyAuthHandler.java
                        return;
                    }
                }
                if (msg instanceof HttpContent content) {
                    if (mjpegUri.equals(requestUrl) && !(content instanceof LastHttpContent)) {
                        // multiple MJPEG stream packets come back as this.
                        byte[] chunkedFrame = new byte[content.content().readableBytes()];
                        content.content().getBytes(content.content().readerIndex(), chunkedFrame);
                        camerasOpenStreams.get(entityID).openStreams.queueFrame(chunkedFrame);
                    } else {

                        // Found some cameras use Content-Type: image/jpg instead of image/jpeg
                        if (contentType.contains("image/jp")) {
                            for (int i = 0; i < content.content().capacity(); i++) {
                                incomingJpeg[bytesAlreadyReceived++] = content.content().getByte(i);
                            }
                            if (content instanceof LastHttpContent) {
                                processSnapshot(incomingJpeg);
                                ctx.close();
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
                                    super.channelRead(ctx, incomingMessage);
                                }
                            }
                            // Alarm Streams never have a LastHttpContent as they always stay open//
                            else if (contentType.contains("multipart")) {
                                int beginIndex, endIndex;
                                if (bytesToReceive == 0) {
                                    beginIndex = incomingMessage.indexOf("Content-Length:");
                                    if (beginIndex != -1) {
                                        endIndex = incomingMessage.indexOf("\r\n", beginIndex);
                                        if (endIndex != -1) {
                                            bytesToReceive = Integer.parseInt(
                                                incomingMessage.substring(beginIndex + 15, endIndex).strip());
                                        }
                                    }
                                }
                                // --boundary and headers are not included in the Content-Length value
                                if (bytesAlreadyReceived > bytesToReceive) {
                                    // Check if message has a second --boundary
                                    endIndex = incomingMessage.indexOf("--" + boundary, bytesToReceive);
                                    Object reply;
                                    if (endIndex == -1) {
                                        reply = incomingMessage;
                                        incomingMessage = "";
                                        bytesToReceive = 0;
                                        bytesAlreadyReceived = 0;
                                    } else {
                                        reply = incomingMessage.substring(0, endIndex);
                                        incomingMessage = incomingMessage.substring(endIndex);
                                        bytesToReceive = 0;// Triggers search next time for Content-Length:
                                        bytesAlreadyReceived = incomingMessage.length() - endIndex;
                                    }
                                    super.channelRead(ctx, reply);
                                }
                            }
                        }
                    }
                } else { // msg is not HttpContent
                    // Foscam cameras need this
                    if (!contentType.contains("image/jp") && bytesAlreadyReceived != 0) {
                        log.trace("[{}]: Packet back from camera is {}", getEntityID(), incomingMessage);
                        super.channelRead(ctx, incomingMessage);
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
            if (evt instanceof IdleStateEvent e) {
                // If camera does not use the channel for X amount of time it will close.
                if (e.state() == IdleState.READER_IDLE) {
                    String urlToKeepOpen = brandHandler.getUrlToKeepOpenForIdleStateEvent();
                    ChannelTracking channelTracking = channelTrackingMap.get(urlToKeepOpen);
                    if (channelTracking != null) {
                        if (channelTracking.getChannel() == ctx.channel()) {
                            return; // don't auto close this as it is for the alarms.
                        }
                    }
                    log.debug("[{}]: Closing an idle channel for camera:{}", entity.getEntityID(), entity.getTitle());
                    ctx.close();
                }
            }
        }
    }
}

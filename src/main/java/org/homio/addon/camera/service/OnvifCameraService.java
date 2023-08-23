package org.homio.addon.camera.service;

import static org.homio.api.util.CommonUtils.getErrorMessage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.onvif.soap.OnvifDeviceState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.onvif.impl.UnknownBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.onvif.util.MyNettyAuthHandler;
import org.homio.addon.camera.service.util.CommonCameraHandler;
import org.homio.addon.camera.service.util.VideoUtils;
import org.homio.addon.camera.ui.UICameraActionConditional;
import org.homio.addon.camera.ui.UICameraDimmerButton;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.addon.camera.ui.UIVideoActionMetadata;
import org.homio.addon.camera.ui.VideoActionType;
import org.homio.api.EntityContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.state.DecimalType;
import org.homio.api.state.ObjectType;
import org.homio.api.state.OnOffType;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.onvif.ver10.schema.Profile;

@Log4j2
public class OnvifCameraService extends BaseVideoService<OnvifCameraEntity, OnvifCameraService> {

    private static @NotNull
    final Map<String, CameraBrandHandlerDescription> cameraBrands = new ConcurrentHashMap<>();
    private final @NotNull ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Getter
    private final @NotNull BaseOnvifCameraBrandHandler brandHandler;

    private final @NotNull EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup(1);
    @Getter
    private final @NotNull OnvifDeviceState onvifDeviceState;
    public boolean useDigestAuth = false;

    private Bootstrap mainBootstrap;
    private @NotNull FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), "");
    private String basicAuth = "";

    public List<LowRequest> lowPriorityRequests = new ArrayList<>(0);
    private byte lowPriorityCounter = 0;

    public OnvifCameraService(EntityContext entityContext, OnvifCameraEntity entity) {
        super(entity, entityContext);

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
        String rtspUri = urls.getRtspUri(profile);
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
    protected boolean pingCamera() {
        try {
            CommonUtils.ping(entity.getIp(), entity.getRestPort());
            return true;
        } catch (Exception ignore) {
        }
        return false;
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
            mainBootstrap.option(ChannelOption.SO_RCVBUF, 10*1024 * 1024);
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
                    socketChannel.pipeline().addLast(IpCameraBindingConstants.COMMON_HANDLER, new CommonCameraHandler(OnvifCameraService.this));

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
            throw new IllegalStateException("Http method: " + httpMethod + " not implemented");
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
                        cameraCommunicationError("Connection Timeout");
                    }
                });
    }

    public void storeHttpReply(String url, String content) {
        ChannelTracking channelTracking = getChannelTrack(url);
        if (channelTracking != null) {
            channelTracking.setReply(content);
        }
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_PAN, order = 3, icon = "fas fa-expand-arrows-alt", type = VideoActionType.slider)
    @UICameraActionConditional(SupportPTZCondition.class)
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

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_PAN)
    public DecimalType getPan() {
        return new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentPanPercentage()));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TILT, order = 5, icon = "fas fa-sort", type = VideoActionType.slider)
    @UICameraActionConditional(SupportPTZCondition.class)
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

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ZOOM, order = 7, icon = "fas fa-search-plus", type = VideoActionType.slider)
    @UICameraActionConditional(SupportPTZCondition.class)
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

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_GOTO_PRESET)
    public DecimalType getGotoPreset() {
        return new DecimalType(0);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_GOTO_PRESET, order = 30, icon = "fas fa-location-arrow")
    @UIVideoActionMetadata(min = 1, max = 25, selectReplacer = "Preset %0  ")
    @UICameraActionConditional(SupportPTZCondition.class)
    public void gotoPreset(int preset) {
        onvifDeviceState.getPtzDevices().gotoPreset(preset);
    }

    public boolean streamIsStopped(String url) {
        ChannelTracking channelTracking = getChannelTrack(url);
        if (channelTracking != null) {
            return !channelTracking.getChannel().isActive(); // stream is running.
        }
        return true; // Stream stopped or never started.
    }

    @Override
    public void openCamerasStream() {
        String mjpegUri = urls.getMjpegUri();
        if (mjpegUri.equals("ffmpeg")) {
            ffmpegMjpeg.startConverting();
        } else {
            closeChannel(getTinyUrl(mjpegUri));
            // Dahua cameras crash if you refresh (close and open) the stream without this delay.
            mainEventLoopGroup.schedule(() -> sendHttpGET(mjpegUri), 300, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void requestSnapshotByUri() {
        // use brandHandler.getSnapshotUri because Reolink uses &token=XXX as auth
        mainEventLoopGroup.schedule(() -> sendHttpGET(brandHandler.getSnapshotUri(true)), 0, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void takeSnapshotSync(@Nullable String profile, Path output) {
        String snapshotUri = urls.getSnapshotUri(profile);
        if (snapshotUri.equals("ffmpeg")) {
            super.takeSnapshotSync(profile, output);
        } else {
            if (snapshotUri.startsWith("/")) {
                snapshotUri = "http://%s:%s%s".formatted(getEntity().getIp(), getEntity().getRestPort(), brandHandler.getSnapshotUri(true));
            }
            VideoUtils.downloadImage(snapshotUri, entity.getUser(), entity.getPassword().asString(), output);
        }
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
                if (Objects.equals(channelTracking.getChannel(), channel)) {
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
        uiInputBuilder.addSelectableButton("VIDEO.FETCH_DATA_FROM_CAMERA", (entityContext, params) -> {
            brandHandler.fetchDataFromCamera();
            entityContext.ui().updateItem(entity);
            return ActionResponseModel.success();
        }).setIcon(new Icon("fas fa-cloud-arrow-down"));
        brandHandler.assembleActions(uiInputBuilder);
    }

    public void addLowRequestGet(String url) {
        this.lowPriorityRequests.add(new LowRequest(() -> sendHttpGET(url)));
    }

    public void addLowRequest(Runnable handler) {
        this.lowPriorityRequests.add(new LowRequest(handler));
    }

    @Override
    protected void pollCameraRunnable() {
        onvifDeviceState.getEventDevices().pollCameraRunnable();
        if (!lowPriorityRequests.isEmpty()) {
            if (lowPriorityCounter >= lowPriorityRequests.size()) {
                lowPriorityCounter = 0;
            }
            lowPriorityRequests.get(lowPriorityCounter++).handler.run();
        }

        super.pollCameraRunnable();
        // what needs to be done every poll//
        brandHandler.pollCameraRunnable();

        if (openChannels.size() > 10) {
            log.debug("[{}]: There are {} open Channels being tracked.", getEntityID(), openChannels.size());
            cleanChannels();
        }
    }

    @Override
    protected void postInitializeCamera() {
        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(),
                entity.getUser(), entity.getPassword().asString());
        if (urls.getSnapshotUri().equals("ffmpeg")) {
            urls.setSnapshotUri(brandHandler.getSnapshotUri(false));
        }
        String mjpegUri = urls.getMjpegUri();
        if (mjpegUri.equals("ffmpeg")) {
            urls.setMjpegUri(brandHandler.getMjpegUri());
        }
        brandHandler.postInitializeCamera(entityContext);
    }

    @Override
    protected void onCameraConnected() {
        brandHandler.onCameraConnected();
    }

    @Override
    protected void offline() {
        super.offline();
        onvifDeviceState.dispose();
        openChannels.close();
    }

    @Override
    protected void dispose0() {
        basicAuth = ""; // clear out stored Password hash
        useDigestAuth = false;
    }

    @Override
    protected void pollCameraConnection() throws Exception {
        onvifDeviceState.initFully(entity.getOnvifMediaProfile(), brandHandler.isSupportOnvifEvents());
        setAttribute("PROFILES", new ObjectType(onvifDeviceState.getProfiles()));
        for (Profile profile : onvifDeviceState.getProfiles()) {
            urls.setRtspUri(onvifDeviceState.getMediaDevices().getRTSPStreamUri(profile.getName()), profile.getName());
            urls.setSnapshotUri(onvifDeviceState.getMediaDevices().getSnapshotUri(profile.getName()), profile.getName());
        }
        // we need reinitialize ffmpeg because rtsp urls has been changed
        recreateFFmpeg();

        super.pollCameraConnection();
    }

    @Override
    public void destroy() {
        super.destroy();
        mainEventLoopGroup.shutdownGracefully();
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

    private int getPortFromShortenedUrl(String httpRequestURL, OnvifCameraEntity entity) {
        if (httpRequestURL.startsWith(":")) {
            int end = httpRequestURL.indexOf("/");
            return Integer.parseInt(httpRequestURL.substring(1, end));
        }
        return entity.getRestPort();
    }

    public ActionResponseModel authenticate() {
        entityContext.ui().sendDialogRequest("cam_auth", "CONTEXT.ACTION.AUTHENTICATE",
                (responseType, pressedButton, parameters) ->
                        fireAuth(parameters),
                dialogModel -> {
                    dialogModel.disableKeepOnUi();
                    dialogModel.appearance(new Icon("fas fa-camera"), null);
                    List<ActionInputParameter> inputs = new ArrayList<>();
                    inputs.add(ActionInputParameter.text("field.user", entity.getUser()));
                    inputs.add(ActionInputParameter.text("field.password", entity.getPassword().asString()));

                    dialogModel.submitButton("CONTEXT.ACTION.AUTHENTICATE", button ->
                            button.setIcon("fas fa-sign-in-alt")
                    ).group("General", inputs);
                });
        return null;
    }

    private void fireAuth(ObjectNode params) {
        entityContext.bgp().runWithProgress("camera-auth").execute(progressBar -> {
            progressBar.progress(10, "Authenticate...");
            try {
                String user = params.get("field.user").asText();
                String password = params.get("field.password").asText();
                OnvifCameraEntity entity = entityContext.getEntityRequire(getEntityID());
                OnvifDeviceState onvifDeviceState = new OnvifDeviceState(getEntityID());
                onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(), user, password);
                CommonUtils.ping(entity.getIp(), entity.getRestPort());
                progressBar.progress(20, "Ping done");
                entity.setInfo(onvifDeviceState, false);
                entityContext.save(entity);
                entityContext.ui().sendSuccessMessage("Onvif camera: " + this + " authenticated successfully");
            } catch (Exception ex) {
                entityContext.ui().sendErrorMessage("Camera fault: %s".formatted(ex.getMessage()));
            } finally {
                progressBar.done();
            }
        });
    }

    public static class SupportPTZCondition implements BiPredicate<OnvifCameraService, Method> {

        @Override
        public boolean test(OnvifCameraService o, Method method) {
            return o.onvifDeviceState.getPtzDevices().supportPTZ();
        }
    }

    @RequiredArgsConstructor
    public static class LowRequest {
        private final Runnable handler;
    }
}

package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.AudioAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.CellMotionAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.FieldDetectAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.LineCrossAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.SceneChangeAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.StorageAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.TamperAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.TooBlurryAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.TooBrightAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.TooDarkAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DELETE_PRESET;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_GOTO_HOME;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_GOTO_PRESET;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN_CONTINUOUS;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN_RELATIVE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_SAVE_GOTO_HOME;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_SAVE_PRESET;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT_CONTINUOUS;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT_RELATIVE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM_CONTINUOUS;
import static org.homio.api.ContextSetting.SERVER_PORT;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingRunnable;
import de.onvif.soap.OnvifDeviceState;
import de.onvif.soap.OnvifUrl;
import de.onvif.soap.devices.PtzDevices;
import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.awt.Dimension;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.homio.addon.camera.CameraConstants.AlarmEvent;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.onvif.impl.UnknownBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.NettyAuthHandler;
import org.homio.addon.camera.service.util.CameraUtils;
import org.homio.addon.camera.service.util.CommonCameraHandler;
import org.homio.api.Context;
import org.homio.api.Context.FileLogger;
import org.homio.api.ContextNetwork;
import org.homio.api.ContextUI.DialogResponseType;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.state.DecimalType;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.onvif.ver10.schema.PTZNode;
import org.onvif.ver10.schema.PTZPreset;
import org.onvif.ver10.schema.PTZSpaces;
import org.onvif.ver10.schema.Profile;
import org.onvif.ver10.schema.VideoResolution;

public class IpCameraService extends BaseCameraService<IpCameraEntity, IpCameraService> {

    private static @NotNull final Map<String, CameraBrandHandlerDescription> cameraBrands = new ConcurrentHashMap<>();
    private final @NotNull ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Getter
    private final @NotNull BaseOnvifCameraBrandHandler brandHandler;

    private final @NotNull EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup(1);
    private Bootstrap mainBootstrap;
    private NettyAuthHandler nettyAuthHandler;

    @Getter
    private final @NotNull OnvifDeviceState onvifDeviceState;
    private final FileLogger onvifEventsLogger;

    private @NotNull FullHttpRequest putRequestWithBody = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), "");

    public List<LowRequest> lowPriorityRequests = new ArrayList<>(0);
    private byte lowPriorityCounter = 0;

    public IpCameraService(Context context, IpCameraEntity entity) {
        super(entity, context);

        onvifEventsLogger = context().getFileLogger(getEntity(), "onvifEvents");

        onvifDeviceState = new OnvifDeviceState(entity.getEntityID());
        onvifDeviceState.setUpdateListener(() -> context.ui().updateItem(entity));

        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(), entity.getUser(), entity.getPassword().asString());

        onvifDeviceState.setUnreachableHandler(message -> this.disposeAndSetStatus(Status.OFFLINE, message));

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, CellMotionAlarm),
            "RuleEngine/CellMotionDetector/Motion");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, MotionAlarm),
            "VideoSource/MotionAlarm");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, AudioAlarm),
            "AudioAnalytics/Audio/DetectedSound");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, FieldDetectAlarm),
            "RuleEngine/FieldDetector/ObjectsInside");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, LineCrossAlarm),
            "RuleEngine/LineDetector/Crossed");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, TamperAlarm),
            "RuleEngine/TamperDetector/Tamper");
        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, StorageAlarm),
            "Device/HardwareFailure/StorageFailure");

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, TooDarkAlarm),
            "VideoSource/ImageTooDark/AnalyticsService",
            "VideoSource/ImageTooDark/ImagingService",
            "VideoSource/ImageTooDark/RecordingService");

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, SceneChangeAlarm),
            "VideoSource/GlobalSceneChange/AnalyticsService",
            "VideoSource/GlobalSceneChange/ImagingService",
            "VideoSource/GlobalSceneChange/RecordingService");

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, TooBrightAlarm),
            "VideoSource/ImageTooBright/AnalyticsService",
            "VideoSource/ImageTooBright/ImagingService",
            "VideoSource/ImageTooBright/RecordingService");

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> onvifSubscribeEvent(dataName, dataValue, TooBlurryAlarm),
            "VideoSource/ImageTooBlurry/AnalyticsService",
            "VideoSource/ImageTooBlurry/ImagingService",
            "VideoSource/ImageTooBlurry/RecordingService");

        onvifDeviceState.getEventDevices().subscribe(
            (dataName, dataValue) -> {
                // do nothing
            },
            "Device/Trigger/DigitalInput",
            "Device/Trigger/Relay");

        if (entity.getCameraType() != null && getCameraBrands(context).containsKey(entity.getCameraType())) {
            CameraBrandHandlerDescription cameraBrandHandlerDescription = getCameraBrands(context).get(entity.getCameraType());
            this.brandHandler = cameraBrandHandlerDescription.newInstance(this);
        } else {
            this.brandHandler = new UnknownBrandHandler(this);
        }
    }

    private void onvifSubscribeEvent(String dataName, String dataValue, AlarmEvent alarmEvent) {
        onvifEventsLogger.logInfo("%s %s: %s".formatted(alarmEvent, dataName, dataValue));
        alarmDetected(dataValue.equals("true"), alarmEvent);
    }

    public static Map<String, CameraBrandHandlerDescription> getCameraBrands(Context context) {
        if (cameraBrands.isEmpty()) {
            for (Class<? extends BaseOnvifCameraBrandHandler> brandHandlerClass :
                context.getClassesWithParent(BaseOnvifCameraBrandHandler.class)) {
                cameraBrands.put(brandHandlerClass.getSimpleName(), new CameraBrandHandlerDescription(brandHandlerClass));
            }
        }
        return cameraBrands;
    }

    public boolean isSupportPtz() {
        return onvifDeviceState.getPtzDevices().supportPTZ();
    }

    @Override
    protected boolean pingCamera() {
        try {
            ContextNetwork.ping(entity.getIp(), entity.getRestPort());
            return true;
        } catch (Exception ignore) {
        }
        return false;
    }

    public CameraPlaybackStorage getVideoPlaybackStorage() {
        return (CameraPlaybackStorage) brandHandler;
    }

    public void sendHttpPUT(String httpRequestURL, FullHttpRequest request) {
        putRequestWithBody = request; // use Global so the auth handler can use it when resent with DIGEST.
        sendHttpRequest("PUT", httpRequestURL, null);
    }

    public void sendHttpGET(String httpRequestURL) {
        sendHttpRequest("GET", httpRequestURL, null);
    }

    // Always use this as sendHttpGET(GET/POST/PUT/DELETE, "/foo/bar",null)//
    // The authHandler will generate a digest string and re-send using this same function when needed.
    @SuppressWarnings("null")
    public void sendHttpRequest(String httpMethod, String httpRequestURLFull, String digestString) {
        IpCameraEntity entity = getEntity();
        int port = getPortFromShortenedUrl(httpRequestURLFull, entity);
        String httpRequestURL = getTinyUrl(httpRequestURLFull);

        if (mainBootstrap == null) {
            nettyAuthHandler = new NettyAuthHandler(this);
            mainBootstrap = new Bootstrap();
            mainBootstrap.group(mainEventLoopGroup);
            mainBootstrap.channel(NioSocketChannel.class);
            mainBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            mainBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4500);
            mainBootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 8);
            mainBootstrap.option(ChannelOption.SO_RCVBUF, 10 * 1024 * 1024);
            mainBootstrap.option(ChannelOption.TCP_NODELAY, true);
            mainBootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @SneakyThrows
                @Override
                public void initChannel(SocketChannel socketChannel) {
                    // HIK Alarm stream needs > 9sec idle to stop stream closing
                    socketChannel.pipeline().addLast(new IdleStateHandler(18, 0, 0));
                    socketChannel.pipeline().addLast(new HttpClientCodec());
                    socketChannel.pipeline().addLast(NettyAuthHandler.AUTH_HANDLER, nettyAuthHandler);
                    socketChannel.pipeline().addLast(CommonCameraHandler.COMMON_HANDLER, new CommonCameraHandler(IpCameraService.this));

                    socketChannel.pipeline().addLast(brandHandler.getClass().getSimpleName(), brandHandler.asBootstrapHandler());
                }
            });
        }

        FullHttpRequest request;
        if ("GET".equals(httpMethod) || (nettyAuthHandler.isUseDigestAuth() && digestString == null)) {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod(httpMethod), httpRequestURL);
            request.headers().set(HttpHeaderNames.HOST, entity.getIp() + ":" + port);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else if ("PUT".equals(httpMethod)) {
            request = putRequestWithBody;
        } else {
            throw new IllegalStateException("Http method: " + httpMethod + " not implemented");
        }

        nettyAuthHandler.authenticateRequest(request, digestString);

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

                             CommonCameraHandler commonHandler = (CommonCameraHandler) ch.pipeline().get(CommonCameraHandler.COMMON_HANDLER);
                             commonHandler.setRequestUrl(httpRequestURLFull);
                             NettyAuthHandler authHandler = (NettyAuthHandler) ch.pipeline().get(NettyAuthHandler.AUTH_HANDLER);
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

    public Profile getProfile() {
        List<Profile> profiles = onvifDeviceState.getProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No onvif profiles found");
        }
        return profiles.get(0);
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

    public boolean streamIsStopped(String url) {
        ChannelTracking channelTracking = getChannelTrack(url);
        if (channelTracking != null) {
            return !channelTracking.getChannel().isActive(); // stream is running.
        }
        return true; // Stream stopped or never started.
    }

    @Override
    public void startMjpegStream(ThrowingRunnable<Exception> destroyListener) {
        if (!startFfmpegMjpeg(destroyListener)) {
            String mjpegUri = urls.getMjpegUri();
            closeChannel(getTinyUrl(mjpegUri));
            // Dahua cameras crash if you refresh (close and open) the stream without this delay.
            mainEventLoopGroup.schedule(() -> sendHttpGET(mjpegUri), 300, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void takeSnapshotAsyncInternal() {
        String snapshotUri = urls.getSnapshotUri();
        if ("ffmpeg".equals(snapshotUri)) {
            super.takeSnapshotAsync();
        } else {
            // snapshotUri must be without ip address and port!
            mainEventLoopGroup.schedule(() -> sendHttpGET(snapshotUri), 0, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    @SneakyThrows
    protected void takeSnapshotSync(@Nullable String profile, @NotNull Path output) {
        String snapshotUri = urls.getSnapshotUri(profile);
        if (snapshotUri.equals("ffmpeg")) {
            super.takeSnapshotSync(profile, output);
        } else {
            if (snapshotUri.startsWith("/")) {
                snapshotUri = "http://%s:%s%s".formatted(getEntity().getIp(), getEntity().getRestPort(), snapshotUri);
            }
            try (InputStream inputStream = CameraUtils.sendRequest(getEntityID(), snapshotUri, entity.getUser(), entity.getPassword().asString())
                                                      .get(10, TimeUnit.SECONDS)) {
                Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public ActionResponseModel authenticate() {
        context.ui().dialog().sendDialogRequest("cam_auth", "AUTHENTICATE",
            (responseType, pressedButton, parameters) ->
                fireAuth(parameters),
            dialogModel -> {
                dialogModel.disableKeepOnUi();
                dialogModel.appearance(new Icon("fas fa-camera"), null);
                List<ActionInputParameter> inputs = new ArrayList<>();
                inputs.add(ActionInputParameter.text("user", entity.getUser(), "min:3"));
                inputs.add(ActionInputParameter.text("password", entity.getPassword().asString()));

                dialogModel.submitButton("AUTHENTICATE", button ->
                    button.setIcon("fas fa-sign-in-alt")
                ).group("General", inputs);
            });
        return null;
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
    public void updateNotificationBlock() {
        CameraEntrypoint.updateCamera(context, getEntity(),
            () -> {
                val brand = getCameraBrands(context).get(entity.getCameraType());
                return entity.getIp() + ":" + entity.getOnvifPort() + " " + brand.getName();
            }, new Icon("fas fa-wifi", "#0E578F"),
            actionBuilder -> actionBuilder.addButton("RESTART", new Icon("fas fa-power-off"),
                (context, params) -> {
                    String response = onvifDeviceState.getInitialDevices().reboot();
                    return ActionResponseModel.showSuccess(response);
                }));
    }

    @Override
    protected void onCameraConnected() {
        addEndpoints();
        brandHandler.onCameraConnected();
    }

    public @NotNull List<OptionModel> getPtzPresets() {
        List<PTZPreset> ptzPresets = onvifDeviceState.getPtzDevices().getPresets(getProfile());
        return ptzPresets
            .stream()
            .map(p -> {
                String description = p.getToken();
                if (p.getPTZPosition() != null) {
                    description += " " + p.getPTZPosition().toString();
                }
                return OptionModel.of(p.getToken(), p.getName()).setDescription(description);
            })
            .sorted()
            .toList();
    }

    private void addEndpoints() {
        if (isSupportPtz()) {
            PtzDevices ptz = onvifDeviceState.getPtzDevices();
            PTZNode ptzNode = ptz.getNode(getProfile());

            if (ptzNode != null) {
                PTZSpaces ptzSpaces = ptzNode.getSupportedPTZSpaces();
                if (ptz.isMoveSupported()) {
                    if (!ptzSpaces.getAbsolutePanTiltPositionSpace().isEmpty()) {
                        addAbsolutePanTilt();
                    }
                    if (!ptzSpaces.getContinuousPanTiltVelocitySpace().isEmpty()) {
                        addPanTilt(ENDPOINT_TILT_CONTINUOUS, ENDPOINT_PAN_CONTINUOUS, true);
                    }
                    if (!ptzSpaces.getRelativePanTiltTranslationSpace().isEmpty()) {
                        addPanTilt(ENDPOINT_TILT_RELATIVE, ENDPOINT_PAN_RELATIVE, false);
                    }
                }
                if (ptz.isZoomSupported()) {
                    addZoomEndpoint(ptzSpaces);
                }

                if (ptzNode.isHomeSupported()) {
                    addEndpointTrigger(ENDPOINT_GOTO_HOME, new Icon("fas fa-house", "##56628c"), null, null, null, state ->
                        ptz.gotoHome(getProfile()));

                    addEndpointTrigger(ENDPOINT_SAVE_GOTO_HOME, new Icon("fas fa-person-shelter", "#2B9679"), null, null, null, state ->
                        ptz.saveHomePosition(getProfile()));
                }

                addEndpointEnum(ENDPOINT_GOTO_PRESET, getPtzPresets(), state ->
                    ptz.gotoPreset(getProfile(), state.stringValue()));

                addEndpointTrigger(ENDPOINT_SAVE_PRESET, new Icon("fas fa-floppy-disk", "#FFD44D"), null, null, null, state ->
                    context.ui().dialog().sendDialogRequest("goto-preset-name", "DIALOG.TITLE.CREATE_GOTO_PRESET",
                        (responseType, pressedButton, parameters) -> {
                            if (responseType == DialogResponseType.Accepted) {
                                String gotoName = parameters.get("name").asText();
                                ptz.savePreset(getProfile(), gotoName, null);
                                refreshGotoPresets();
                            }
                        }, builder -> {
                            builder.disableKeepOnUi();
                            builder.appearance(new Icon("fas fa-camera"), null);
                            List<ActionInputParameter> inputs = new ArrayList<>();
                            inputs.add(ActionInputParameter.text("name", "Sample preset", "min:3"));
                            builder.group("General", inputs);
                        }));

                addEndpointTrigger(ENDPOINT_DELETE_PRESET, new Icon("fas fa-trash"), null, null, null, state -> {
                    List<OptionModel> presets = getPtzPresets();
                    if (presets.isEmpty()) {
                        throw new ServerException("W.ERROR.NO_GOTO_PRESETS");
                    }
                    context.ui().dialog().sendDialogRequest("remove-goto-preset", "DIALOG.TITLE.REMOVE_GOTO_PRESET",
                        (responseType, pressedButton, parameters) -> {
                            String gotoName = parameters.get("name").asText();
                            PTZPreset preset = ptz.getPresets(getProfile()).stream().filter(p -> p.getToken().equals(gotoName)).findAny().orElse(null);
                            if (preset != null) {
                                ptz.removePreset(getProfile(), preset);
                                refreshGotoPresets();
                            }
                        }, builder -> {
                            builder.appearance(new Icon("fas fa-trash"), Color.ERROR_DIALOG);
                            builder.group("General",
                                List.of(ActionInputParameter.select("name", presets.get(0).getKey(), presets)));
                        });
                });
            }
        }
    }

    private void refreshGotoPresets() {
        CameraDeviceEndpoint gotoPreset = getEndpoints().get(ENDPOINT_GOTO_PRESET);
        gotoPreset.setRange(getPtzPresets());
        gotoPreset.updateUI();
    }

    private void addPanTilt(String tiltId, String panId, boolean continuous) {
        addEndpointButtons(tiltId, List.of(
            OptionModel.of("UP").setIcon("fas fa-caret-up"),
            OptionModel.of("OFF").setIcon("fas fa-power-off"),
            OptionModel.of("DOWN").setIcon("fas fa-caret-down")
        ), state -> {
            String command = state.stringValue();
            if ("UP".equals(command)) {
                onvifDeviceState.getPtzDevices().moveUp(continuous, getProfile());
            } else if ("DOWN".equals(command)) {
                onvifDeviceState.getPtzDevices().moveDown(continuous, getProfile());
            } else if ("OFF".equals(command)) {
                onvifDeviceState.getPtzDevices().stopMove(getProfile());
            } else {
                throw new IllegalStateException("Illegal TILT command: " + command);
            }
        });

        addEndpointButtons(panId, List.of(
            OptionModel.of("LEFT").setIcon("fas fa-caret-left"),
            OptionModel.of("OFF").setIcon("fas fa-power-off"),
            OptionModel.of("RIGHT").setIcon("fas fa-caret-right")
        ), state -> {
            String command = state.stringValue();
            if ("LEFT".equals(command)) {
                onvifDeviceState.getPtzDevices().moveLeft(continuous, getProfile());
            } else if ("RIGHT".equals(command)) {
                onvifDeviceState.getPtzDevices().moveRight(continuous, getProfile());
            } else if ("OFF".equals(command)) {
                onvifDeviceState.getPtzDevices().stopMove(getProfile());
            } else {
                throw new IllegalStateException("Illegal TILT command: " + command);
            }
        });
    }

    private void addAbsolutePanTilt() {
        PtzDevices ptz = onvifDeviceState.getPtzDevices();
        addEndpointSlider(ENDPOINT_TILT, 0F, 100F, state ->
            ptz.setAbsoluteTilt(state.floatValue(), getProfile()), true).setValue(
            new DecimalType(Math.round(ptz.getCurrentTiltPercentage())), false);

        addEndpointSlider(ENDPOINT_PAN, 0F, 100F, state ->
            ptz.setAbsolutePan(state.floatValue(), getProfile()), true)
            .setValue(new DecimalType(Math.round(ptz.getCurrentPanPercentage())), false);
    }

    private void addZoomEndpoint(PTZSpaces ptzSpaces) {
        if (!ptzSpaces.getAbsoluteZoomPositionSpace().isEmpty()) {
            addEndpointSlider(ENDPOINT_ZOOM, 0F, 100F, state ->
                onvifDeviceState.getPtzDevices().setAbsoluteZoom(state.floatValue(), getProfile()), true).setValue(
                new DecimalType(Math.round(onvifDeviceState.getPtzDevices().getCurrentZoomPercentage())), false);
        }

        if (!ptzSpaces.getContinuousZoomVelocitySpace().isEmpty()) {
            addEndpointButtons(ENDPOINT_ZOOM_CONTINUOUS, List.of(
                OptionModel.of("IN").setIcon("fas fa-search-plus"),
                OptionModel.of("OFF").setIcon("fas fa-power-off"),
                OptionModel.of("OUT").setIcon("fas fa-search-minus")
            ), state -> {
                String command = state.stringValue();
                if ("IN".equals(command)) {
                    onvifDeviceState.getPtzDevices().moveIn(false, getProfile());
                } else if ("OUT".equals(command)) {
                    onvifDeviceState.getPtzDevices().moveOut(false, getProfile());
                } else if ("OFF".equals(command)) {
                    onvifDeviceState.getPtzDevices().stopMove(getProfile());
                } else {
                    throw new IllegalStateException("Illegal ZOOM command: " + command);
                }
            });
        }
    }

    @Override
    protected void offline() {
        super.offline();
        onvifDeviceState.dispose();
        openChannels.close();
    }

    @Override
    protected void dispose0() {
        if(nettyAuthHandler != null) {
            nettyAuthHandler.dispose();
        }
    }

    @Override
    protected void postInitializeCamera() {
        onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(),
            entity.getUser(), entity.getPassword().asString());
        urls.setSnapshotUri(brandHandler.getSnapshotUri());
        urls.setMjpegUri(brandHandler.getMjpegUri());
        brandHandler.postInitializeCamera(context);
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
        super.destroy(forRestart, ex);
        mainEventLoopGroup.shutdownGracefully();
    }

    @Override
    protected void setAudioAlarmThreshold(int audioThreshold) {
        brandHandler.setAudioAlarmThreshold(audioThreshold);
    }

    @Override
    protected void setMotionAlarmThreshold(int motionThreshold) {
        brandHandler.setMotionAlarmThreshold(motionThreshold);
    }

    @Override
    protected boolean isAudioAlarmHandlesByVideo() {
        return brandHandler.isHasAudioAlarm();
    }

    @Override
    protected boolean isMotionAlarmHandlesByVideo() {
        return brandHandler.isHasMotionAlarm();
    }

    private int getPortFromShortenedUrl(String httpRequestURL, IpCameraEntity entity) {
        if (httpRequestURL.startsWith(":")) {
            int end = httpRequestURL.indexOf("/");
            return Integer.parseInt(httpRequestURL.substring(1, end));
        }
        return entity.getRestPort();
    }

    @Override
    public List<OptionModel> getLogSources() {
        ArrayList<OptionModel> list = new ArrayList<>(super.getLogSources());
        list.add(OptionModel.of("onvifEvents", "Onvif events"));
        return list;
    }

    @Override
    public @Nullable InputStream getSourceLogInputStream(@NotNull String sourceID) {
        if ("onvifEvents".equals(sourceID)) {
            return onvifEventsLogger.getFileInputStream();
        }
        return super.getSourceLogInputStream(sourceID);
    }

    @Override
    protected void pollCameraConnection() throws Exception {
        onvifDeviceState.initFully();
        if (brandHandler.isSupportOnvifEvents()) {
            String subscribeUrl = "%s:%s/rest/media/video/%s/OnvifEvent".formatted(onvifDeviceState.getIp(), SERVER_PORT, entityID);
            onvifDeviceState.getEventDevices().initFully(subscribeUrl);
        }
        for (Profile profile : onvifDeviceState.getProfiles()) {
            try {
                OnvifUrl rtspStreamUri = onvifDeviceState.getMediaDevices().getRTSPStreamUri(profile.getToken());
                if (rtspStreamUri != null) {
                    urls.setRtspUri(rtspStreamUri.getFullUrl(), profile.getName());
                }
            } catch (Exception ex) {
                log.warn("[{}]: Unable to fetch rtsp url for profile: {}", entityID, profile.getName());
            }
            try {
            OnvifUrl snapshotUri = onvifDeviceState.getMediaDevices().getSnapshotUri(profile.getToken());
            if (snapshotUri != null) {
                urls.setSnapshotUri(snapshotUri.getXAddr(), profile.getName());
            }
            } catch (Exception ex) {
                log.warn("[{}]: Unable to fetch snapshot url for profile: {}", entityID, profile.getName());
            }
        }
        List<Dimension> resolutions = onvifDeviceState
            .getProfiles().stream()
            .map(profile -> {
                VideoResolution resolution = profile.getVideoEncoderConfiguration().getResolution();
                return new Dimension(resolution.getWidth(), resolution.getHeight());
            })
            .toList();
        if (!resolutions.isEmpty()) {
            context.db().updateDelayed(entity, e -> {
                if (entity.getStreamResolutions().isEmpty()) {
                    e.setStreamResolutions(resolutions.stream()
                                                      .sorted(Comparator.comparingInt(o -> o.width + o.height))
                                                      .map(r -> String.format("%dx%d", r.width, r.height))
                                                      .collect(Collectors.joining(LIST_DELIMITER)));
                }
                if (entity.getHlsLowResolution().isEmpty()) {
                    Dimension dim = resolutions.stream().min(Comparator.comparingInt(dim2 -> dim2.width * dim2.height)).get();
                    e.setHlsLowResolution(String.format("%dx%d", dim.width, dim.height));
                }
                if (entity.getHlsHighResolution().isEmpty()) {
                    Dimension dim = resolutions.stream().max(Comparator.comparingInt(dim2 -> dim2.width * dim2.height)).get();
                    e.setHlsHighResolution(String.format("%dx%d", dim.width, dim.height));
                }
            });
        }
        // we need reinitialize ffmpeg because rtsp urls has been changed
        recreateFFmpeg();

        // register rtsp url in MediaMTX
        String rtspUri = urls.getRtspUri();
        if (rtspUri.startsWith("rtsp://") && !rtspUri.contains("@") && isNotEmpty(entity.getUser())) {
            rtspUri = "rtsp://" + entity.getUser() + ":" + entity.getPassword().asString() + "@" + rtspUri.substring("rtsp://".length());
        }
        context.media().registerVideoSource(getEntityID(), rtspUri);

        super.pollCameraConnection();
    }

    private void fireAuth(ObjectNode params) {
        context.bgp().runWithProgress("camera-auth").execute(progressBar -> {
            progressBar.progress(10, "Authenticate...");
            try {
                String user = params.get("user").asText();
                String password = params.get("password").asText();
                IpCameraEntity entity = context.db().getEntityRequire(getEntityID());
                OnvifDeviceState onvifDeviceState = new OnvifDeviceState(getEntityID());
                onvifDeviceState.updateParameters(entity.getIp(), entity.getOnvifPort(), user, password);
                ContextNetwork.ping(entity.getIp(), entity.getRestPort());
                progressBar.progress(20, "Ping done");
                entity.setInfo(onvifDeviceState, false);
                context.db().save(entity);
                context.ui().toastr().success("Onvif camera: " + getEntity() + " authenticated successfully");
            } catch (Exception ex) {
                context.ui().toastr().error("Camera fault: %s".formatted(ex.getMessage()));
            } finally {
                progressBar.done();
            }
        });
    }

    @RequiredArgsConstructor
    public static class LowRequest {

        private final Runnable handler;
    }
}

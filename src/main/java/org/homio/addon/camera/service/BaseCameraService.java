package org.homio.addon.camera.service;

import static java.lang.String.join;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_MOTION_SCORE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_MOTION_THRESHOLD;
import static org.homio.addon.camera.CameraController.camerasOpenStreams;
import static org.homio.addon.camera.entity.StreamMJPEG.mp4OutOptions;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.INITIALIZE;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.REQUIRE_AUTH;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;

import com.pivovarit.function.ThrowingRunnable;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.homio.addon.camera.CameraConstants;
import org.homio.addon.camera.CameraController;
import org.homio.addon.camera.CameraController.OpenStreamsContainer;
import org.homio.addon.camera.ConfigurationException;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.StreamHLS;
import org.homio.addon.camera.entity.VideoMotionAlarmProvider;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.service.util.VideoUrls;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.ContextMedia.FFMPEG;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.ContextMedia.FFMPEGHandler;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.UpdatableValue;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.UI.Image.Snapshot;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;

@SuppressWarnings({"unused"})
public abstract class BaseCameraService<T extends BaseCameraEntity<T, S>, S extends BaseCameraService<T, S>>
    extends EntityService.ServiceInstance<T> implements FFMPEGHandler {

    public static final Path SHARE_DIR = CommonUtils.getTmpPath();
    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
        new ConfigDeviceDefinitionService("camera-devices.json");
    private static final int MAX_PING_ERRORS = 10;

    private final @NotNull @Getter Map<String, CameraDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private @Getter int communicationError;
    private @Getter boolean alarmDetected;

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        return CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), Set.of());
    }

    public List<OptionModel> getLogSources() {
        return List.of(
            OptionModel.of("mjpeg", "FFMPEG mjpeg"),
            OptionModel.of("main", "FFMPEG hls/dash"),
            OptionModel.of("snapshot", "FFMPEG snapshot"));
    }

    public @Nullable InputStream getSourceLogInputStream(@NotNull String sourceID) {
        return switch (sourceID) {
            case "mjpeg" -> getFFMPEGLogInputStream(ffmpegMjpeg);
            case "snapshot" -> getFFMPEGLogInputStream(ffmpegSnapshot);
            case "main" -> getFFMPEGLogInputStream(ffmpegMainReStream);
            default -> null;
        };
    }

    public String getHlsUri() {
        return urls.getRtspUri();
    }

    public String getDashUri() {
        return urls.getRtspUri();
    }

    public FFMPEG[] getFfmpegCommands() {
        return new FFMPEG[]{ffmpegMjpeg, ffmpegSnapshot, ffmpegMainReStream};
    }

    protected final @Nullable InputStream getFFMPEGLogInputStream(@Nullable FFMPEG ffmpeg) {
        return FFMPEG.check(ffmpeg, f -> f.getFileLogger().getFileInputStream(), null);
    }

    public abstract void updateNotificationBlock();

    protected abstract boolean pingCamera();

    protected abstract void dispose0();

    protected abstract void postInitializeCamera();

    protected @NotNull Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();

    private @Getter Path ffmpegOutputPath;
    private @Getter Path ffmpegGifOutputPath;
    private @Getter Path ffmpegMP4OutputPath;
    private @Getter Path ffmpegImageOutputPath;

    private @Getter @Nullable FFMPEG ffmpegMainReStream;
    private @Getter @Nullable FFMPEG ffmpegSnapshot;
    private @Getter @Nullable FFMPEG ffmpegMjpeg;

    // run every 8 seconds to send requests to camera, etc...
    protected @Nullable ThreadContext<Void> pollCameraJob;
    // used to try to connect to camera
    protected @Nullable ThreadContext<Void> cameraConnectionJob;

    // actions holder
    protected @NotNull UpdatableValue<UIInputBuilder> uiInputBuilder = UpdatableValue.deferred("cam-actions", UIInputBuilder.class);

    protected final String gifOutOptions = "-r 2 -filter_complex scale=-2:360:flags=lanczos,setpts=0.5*PTS,split[o1][o2];[o1]palettegen[p];[o2]fifo[o3];"
        + "[o3][p]paletteuse";

    protected long videoStreamParametersHashCode;

    protected @Setter boolean streamingAutoFps = false;

    private @Getter
    @Setter
    @NotNull String mjpegContentType = "";
    public final @Getter VideoUrls urls = new VideoUrls();
    private final @Getter Snapshot snapshot = new Snapshot(30);

    public BaseCameraService(T entity, Context context) {
        super(context, entity, true);
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer(entity));
    }

    public void cameraCommunicationError(String reason) {
        if (communicationError++ > MAX_PING_ERRORS) {
            updateEntityStatus(ERROR, reason);
            resetAndRetryConnecting();
        }
        context.ui().updateItem(entity);
    }

    protected void pollCameraConnection() throws Exception {
        keepMjpegRunning();
        takeSnapshotAsync();
        bringCameraOnline();
    }

    public final void disposeAndSetStatus(@NotNull Status status, @Nullable String reason) {
        if (updateEntityStatus(status, reason)) {
            log.warn("[{}]: Set video <{}> to status <{}>. Msg: <{}>", entityID, entity, status, reason);
            resetAndRetryConnecting();

            if (status == Status.ERROR || status == REQUIRE_AUTH) {
                context.ui().toastr().error("DISPOSE_VIDEO",
                    FlowMap.of("TITLE", entity.getTitle(), "REASON", reason));
            }
        }
    }

    public final void bringCameraOnline() {
        communicationError = 0;
        if (!entity.isStart()) {
            return;
        }
        updateLastSeen();

        if (!entity.getStatus().isOnline()) {
            updateEntityStatus(ONLINE, null);
            entity.getVideoMotionAlarmProviderImpl().resumeMotionAlarmListeners(entity);
            onCameraConnected();

            pollCameraJob = context.bgp().builder("video-poll-" + entityID)
                                         .delay(Duration.ofSeconds(8))
                                         .interval(Duration.ofSeconds(8))
                                         .execute(this::pollCameraRunnable);

            // auto restart mjpeg stream now camera is back online.
            OpenStreamsContainer container = camerasOpenStreams.get(entityID);
            if (container != null) {
                if (!container.getOpenStreams().isEmpty()) {
                    CameraController.startMjpegStream(this);
                }
            }
        }
    }

    protected void resetAndRetryConnecting() {
        offline();
        createConnectionJob();
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) {
        dispose();
        if(!forRestart) {
            deleteDirectories();
        }
    }

    // assemble camera actions and cache every minute
    public UIInputBuilder assembleActions() {
        return uiInputBuilder.getFreshValue(Duration.ofSeconds(60), () -> {
            UIInputBuilder builder = context.ui().inputBuilder();
            assembleAdditionalVideoActions(builder);
            return builder;
        });
    }

    public void motionDetected(@Nullable DecimalType score) {
        alarmDetected(score != null, MotionAlarm);
        DecimalType value = score == null ? DecimalType.ZERO : new DecimalType(BigDecimal.valueOf(score.floatValue() * 100).setScale(2, RoundingMode.HALF_UP));
        addEndpoint(ENDPOINT_MOTION_SCORE, key -> new CameraDeviceEndpoint(entity, context(), key, EndpointType.number, false), null)
            .setValue(value);
    }

    public void processSnapshot(byte[] incomingSnapshot) {
        log.debug("[{}]: Gеt video snapshot: <{}>", getEntityID(), getEntity());
        if (!snapshot.setSnapshot(incomingSnapshot)) {
            context.ui().updateItem(getEntity(), "snapshot", incomingSnapshot);
        }
        updateLastSeen();
    }

    protected void onCameraConnected() {
    }

    public void startMjpegStream(ThrowingRunnable<Exception> destroyListener) {
        startFfmpegMjpeg(destroyListener);
    }

    public boolean startFfmpegMjpeg(ThrowingRunnable<Exception> destroyListener) {
        if ("ffmpeg".equals(urls.getMjpegUri())) {
            FFMPEG.run(ffmpegMjpeg, ffmpeg -> ffmpeg.stopConverting(Duration.ofSeconds(10)));
            ffmpegMjpeg = entity.buildMjpegFFMPEG(this);
            ffmpegMjpeg.addDestroyListener("mjpeg", destroyListener);
            ffmpegMjpeg.startConverting();
            return true;
        }
        return false;
    }

    public void ffmpegError(@NotNull String error) {
        log.error("[{}]: FFMPEG error: {}", entityID, error);
        context.ui().toastr().error("FFMPEG error: ''" + entityID + ". " + error);
    }

    public void deleteDirectories() {
        CommonUtils.deletePath(ffmpegGifOutputPath);
        CommonUtils.deletePath(ffmpegMP4OutputPath);
        CommonUtils.deletePath(ffmpegImageOutputPath);
    }

    public void alarmDetected(boolean on, @NotNull CameraConstants.AlarmEvent event) {
        addEndpointOptional(event).setValue(OnOffType.of(on), true);
        this.alarmDetected = on;
    }

    public byte[] getLastSnapshot() {
        return snapshot.getSnapshot(() -> {
            if (!entity.isStart() || !entity.getStatus().isOnline() || ffmpegSnapshot == null) {
                throw new RuntimeException();
            }
            if (!ffmpegSnapshot.isRunning()) {
                takeSnapshotAsync();
            }
            return null;
        });
    }

    public final void takeSnapshotAsync() {
        snapshot.setLastSnapshotRequest(Instant.now());
        takeSnapshotAsyncInternal();
    }

    public void recordGifAsync(Path filePath, @Nullable String profile, int secondsToRecord) {
        String gifInputOptions = "-y -t " + secondsToRecord + " -hide_banner";
        FFMPEG ffmpegGIF = context.media().buildFFMPEG(entityID, "FFMPEG GIF", new FFMPEGHandler() {}, FFMPEGFormat.GIF,
            gifInputOptions, urls.getRtspUri(profile),
            gifOutOptions, filePath.toString(), entity.getUser(),
            entity.getPassword().asString());
        FFMPEG.run(ffmpegGIF, FFMPEG::startConverting);
    }

    public void assertOnline() {
        if (!entity.getStatus().isOnline()) {
            throw new ServerException("W.ERROR.VIDEO_OFFLINE");
        }
    }

    public void addMotionAlarmListener(String listener) {
        if (!isMotionAlarmHandlesByVideo()) {
            VideoMotionAlarmProvider provider = entity.getVideoMotionAlarmProviderImpl();
            provider.addMotionAlarmListener(entity, listener);
        }
    }

    @Override
    protected void firstInitialize() {
        log.info("[{}]: Initialize", entityID);
        ffmpegOutputPath = CommonUtils.getMediaPath().resolve(BaseCameraEntity.FOLDER).resolve(entityID);
        ffmpegImageOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("images"));
        ffmpegGifOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("gif"));
        ffmpegMP4OutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("mp4"));
        initialize();
    }

    public void removeMotionAlarmListener(String listener) {
        if (!isMotionAlarmHandlesByVideo()) {
            VideoMotionAlarmProvider provider = entity.getVideoMotionAlarmProviderImpl();
            provider.removeMotionAlarmListener(entity, listener);
        }
    }

    @SneakyThrows
    public byte[] recordGifSync(String profile, int secondsToRecord) {
        Path output = getFfmpegGifOutputPath().resolve("tmp_" + System.currentTimeMillis() + ".gif");
        fireFfmpegSync(profile, output, "-t " + secondsToRecord,
            gifOutOptions, secondsToRecord + 20);
        return Files.readAllBytes(output);
    }

    @SneakyThrows
    public byte[] recordMp4Sync(String profile, int secondsToRecord) {
        Path output = getFfmpegMP4OutputPath().resolve("tmp_" + System.currentTimeMillis() + ".mp4");
        fireFfmpegSync(profile, output, "-t " + secondsToRecord,
            mp4OutOptions, secondsToRecord + 20);
        return Files.readAllBytes(output);
    }

    @Override
    public void entityUpdated(@NotNull T newEntity) {
        super.entityUpdated(newEntity);
    }

    @Override
    @SneakyThrows
    protected final void initialize() {
        this.urls.clear();
        createOrUpdateDeviceGroup();
        addPrimaryEndpoint();
        updateEntityStatus(INITIALIZE, null);
        log.info("[{}]: Initialize camera: <{}>", entityID, getEntity());
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer(entity));

        try {
            postInitializeCamera();
            recreateFFmpeg();

            createConnectionJob();
        } catch (Exception ex) {
            disposeAndSetStatus(Status.ERROR, CommonUtils.getErrorMessage(ex));
        }
    }

    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {

    }

    public CameraDeviceEndpoint addEndpointButtons(String endpointId, List<OptionModel> buttons, Consumer<State> updateHandler) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(), key, buttons, true) {
            @Override
            public UIInputBuilder createSelectActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
                uiInputBuilder.addMultiButton(getEntityID(), (context, params) -> {
                    updateHandler.accept(new StringType(params.getString("value")));
                    return null;
                }, 0).addButtons(buttons);
                return uiInputBuilder;
            }
        }, updateHandler);
    }

    public CameraDeviceEndpoint addEndpoint(
        @NotNull String endpointId,
        @NotNull Function<String, CameraDeviceEndpoint> handler,
        @Nullable Consumer<State> updateHandler) {
        return endpoints.computeIfAbsent(endpointId, key -> {
            CameraDeviceEndpoint endpoint = handler.apply(key);
            endpoint.setUpdateHandler(updateHandler);

            /*State attribute = getAttribute(endpointId);
            if (attribute != null) {
                endpoint.setInitialValue(attribute);
            }*/
            return endpoint;
        });
    }

    public CameraDeviceEndpoint addEndpointSwitch(
        @NotNull String endpointId,
        @NotNull Consumer<State> updateHandler) {
        return addEndpointSwitch(endpointId, updateHandler, true);
    }

    protected boolean isAudioAlarmHandlesByVideo() {
        return false;
    }

    protected boolean isMotionAlarmHandlesByVideo() {
        return false;
    }

    public boolean hasAudioStream() {
        return entity.isHasAudioStream();
    }

    public CameraDeviceEndpoint addEndpointInput(String endpointId, Consumer<State> updateHandler) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(), key, EndpointType.string, true), updateHandler);
    }

    private synchronized void dispose() {
        log.info("[{}]: Dispose camera: <{}>", entityID, getEntity());
        offline();

        OpenStreamsContainer container = camerasOpenStreams.remove(entityID);
        if (container != null) {
            container.dispose();
        }
        channelTrackingMap.clear();

        try {
            dispose0();
        } catch (Exception ex) {
            log.error("[{}]: Error while dispose video: <{}>", entityID, getEntity(), ex);
        }
    }

    public CameraDeviceEndpoint addEndpointSwitch(
        @NotNull String endpointId,
        @NotNull Consumer<State> updateHandler,
        boolean writable) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(), key, EndpointType.bool, writable), updateHandler);
    }

    protected void pollCameraRunnable() {
        FFMPEG.run(ffmpegMainReStream, FFMPEG::stopProcessIfNoKeepAlive);
        // keep mjpeg alive forever. Should be handled by CameraController.
        // FFMPEG.run(ffmpegMjpeg, FFMPEG::stopProcessIfNoKeepAlive);

        if (FFMPEG.check(ffmpegMainReStream, FFMPEG::getIsAlive, false)) {
            return;
        }
        takeSnapshotAsync();
        if (!pingCamera()) {
            cameraCommunicationError(
                "Connection Timeout: Check your IP and PORT are correct and the camera can be reached.");
        }
    }

    public CameraDeviceEndpoint addEndpointTrigger(
        @NotNull String endpointId,
        @NotNull Icon buttonIcon,
        @Nullable String text,
        @Nullable String confirmMessage,
        @Nullable String confirmDialogColor,
        @NotNull Consumer<State> updateHandler) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(),
            key, EndpointType.trigger, true) {
            @Override
            public UIInputBuilder createTriggerActionBuilder(@NotNull UIInputBuilder uiInputBuilder) {
                uiInputBuilder.addButton(getEntityID(), buttonIcon, (context, params) -> {
                                  updateHandler.accept(null);
                                  return null;
                              })
                              .setText(StringUtils.trimToEmpty(text))
                              .setConfirmMessage(confirmMessage)
                              .setConfirmMessageDialogColor(confirmDialogColor)
                              .setDisabled(!getDevice().getStatus().isOnline());
                return uiInputBuilder;
            }
        }, updateHandler);
    }

    protected void takeSnapshotAsyncInternal() {
        FFMPEG.run(ffmpegSnapshot, FFMPEG::startConverting);
    }

    protected void takeSnapshotSync(@Nullable String profile, @NotNull Path output) {
        fireFfmpegSync(profile, output, "", entity.getSnapshotOutOptions(), 20);
    }

    public String getTinyUrl(String httpRequestURL) {
        if (httpRequestURL.startsWith(":")) {
            int beginIndex = httpRequestURL.indexOf("/");
            return httpRequestURL.substring(beginIndex);
        }
        return httpRequestURL;
    }

    public ChannelTracking getChannelTrack(String url) {
        return channelTrackingMap.get(url);
    }

    public void closeChannel(String url) {
        ChannelTracking channelTracking = getChannelTrack(url);
        if (channelTracking != null) {
            if (channelTracking.getChannel().isOpen()) {
                channelTracking.getChannel().close();
            }
        }
    }

    public void recordMp4Async(@NotNull Path filePath, @Nullable String profile, int secondsToRecord) {
        entity.recordMp4Async(filePath, profile, secondsToRecord, this);
    }

    public CameraDeviceEndpoint addEndpointEnum(String endpointId, List<OptionModel> range, Consumer<State> updateHandler) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(), key, range, true), updateHandler);
    }

    public void updateLastSeen() {
        CameraDeviceEndpoint endpoint = endpoints.get(ENDPOINT_LAST_SEEN);
        // update not often than 1s
        if(System.currentTimeMillis() - endpoint.getValue().longValue() > 1000) {
            endpoint.setValue(new DecimalType(System.currentTimeMillis()), true);
        }
    }

    void recreateFFmpeg() {
        FFMPEG.run(ffmpegMjpeg, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegSnapshot, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegMainReStream, FFMPEG::stopConverting);

        ffmpegSnapshot = entity.buildSnapshotFFMPEG(this);
    }

    public CameraDeviceEndpoint addEndpointSlider(String endpointId, Float min, Float max, Consumer<State> updateHandler, boolean writable) {
        return addEndpoint(endpointId, key -> new CameraDeviceEndpoint(entity, context(), key, min, max, writable), updateHandler);
    }

    private String getGroupDescription() {
        if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
            return entity.getIeeeAddress();
        }
        return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
    }

    protected void keepMjpegRunning() {
        OpenStreamsContainer container = camerasOpenStreams.get(entityID);
        if (!container.getOpenStreams().isEmpty()) {
            String mjpegUri = urls.getMjpegUri();
            if (!mjpegUri.isEmpty() && !"ffmpeg".equals(mjpegUri)) {
                container.getOpenStreams().queueFrame(("--" + container.getOpenStreams().boundary + "\r\n\r\n").getBytes());
            }
            container.getOpenStreams().queueFrame(getLastSnapshot());
        }
    }

    protected void offline() {
        if (ContextBGP.cancel(pollCameraJob)) {
            pollCameraJob = null;
        }
        if (ContextBGP.cancel(cameraConnectionJob)) {
            cameraConnectionJob = null;
        }

        FFMPEG.run(ffmpegMainReStream, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegMjpeg, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegSnapshot, FFMPEG::stopConverting);

        entity.getVideoMotionAlarmProviderImpl().suspendMotionAlarmListeners(entity);
    }

    private void createConnectionJob() {
        cameraConnectionJob = context
            .bgp().builder("video-connect-" + entityID)
            .intervalWithDelay(Duration.ofSeconds(30))
            .execute(() -> {
                try {
                    if (!entity.getStatus().isOnline()) {
                        pollCameraConnection();
                    }
                    if (entity.getStatus().isOnline() && ContextBGP.cancel(cameraConnectionJob)) {
                        cameraConnectionJob = null;
                    }
                } catch (Exception ex) {
                    String message = CommonUtils.getErrorMessage(ex);
                    if (ex instanceof ConfigurationException
                        || ex instanceof BadCredentialsException) {
                        val status = ex instanceof BadCredentialsException ? REQUIRE_AUTH : ERROR;
                        disposeAndSetStatus(status, message);
                    } else {
                        updateEntityStatus(OFFLINE, message);
                    }
                }
            });
    }

    protected void setMotionAlarmThreshold(int threshold) {
    }

    private boolean updateEntityStatus(Status status, String reason) {
        if (entity.getStatus() != status || !Objects.equals(reason, entity.getStatusMessage())) {
            entity.setStatus(status, reason);
            CameraDeviceEndpoint endpoint = getEndpoints().get(ENDPOINT_DEVICE_STATUS);
            if (endpoint != null) {
                endpoint.setValue(new StringType(status.toString()), false);
            }
            // fully update entity due status/message/etc.. may be changed
            context.ui().updateItem(entity);
            updateNotificationBlock();
            return true;
        }
        return false;
    }

    @SneakyThrows
    private void fireFfmpegSync(@Nullable String profile, @NotNull Path output, @NotNull String inputArguments, @NotNull String outOptions, int maxTimeout) {
        String input = urls.getSnapshotUri(profile);
        if (input.equals("ffmpeg")) {
            input = urls.getRtspUri(profile);
            if (input.equals("ffmpeg")) {
                throw new IllegalStateException("Unable to take snapshot without snapshot/rtsp urls");
            }
        }
        Set<String> inputs = new HashSet<>();
        inputs.add("-y");
        inputs.add("-hide_banner");
        inputs.add(inputArguments);
        context.media().fireFfmpeg(join(" ", inputs), input, outOptions + " " + output, maxTimeout);
    }

    private void createOrUpdateDeviceGroup() {
        List<ConfigDeviceDefinition> devices = findDevices();
        context.var().createGroup("video", "Video", builder ->
            builder.setLocked(true).setIcon(new Icon("fas fa-video", "#0E578F")));
        context.var().createSubGroup("video", entity.getGroupID(), entity.getDeviceFullName(), builder ->
            builder.setLocked(true).setDescription(getGroupDescription()).setIcon(entity.getEntityIcon()));
    }

    protected void setAudioAlarmThreshold(int threshold) {
    }

    private void addPrimaryEndpoint() {
        addEndpoint(ENDPOINT_DEVICE_STATUS, key -> {
            List<OptionModel> range = OptionModel.list(Status.set(ONLINE, ERROR, OFFLINE, REQUIRE_AUTH, UNKNOWN, INITIALIZE));
            return new CameraDeviceEndpoint(entity, context(), key, range, false);
        }, null);

        addEndpoint(ENDPOINT_LAST_SEEN, key -> {
            CameraDeviceEndpoint videoEndpoint = new CameraDeviceEndpoint(entity, context(), key, EndpointType.number, false) {

                @Override
                public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
                    uiInputBuilder.addDuration(getValue().longValue(), null);
                }
            };
            videoEndpoint.setInitialValue(new DecimalType(System.currentTimeMillis()));
            return videoEndpoint;
        }, null);

        addEndpointSlider(ENDPOINT_MOTION_THRESHOLD, 0F, 50F, state -> {
            if (entity.setMotionThreshold(state.intValue())) {
                context.db().save(entity);
                setMotionAlarmThreshold(state.intValue());
                refreshVideoMotionAlarmProviders();
            }
        }, true).setValue(new DecimalType(entity.getMotionThreshold()), false);

        addEndpointSlider(ENDPOINT_AUDIO_THRESHOLD, 0F, 50F, state -> {
            if (entity.setAudioThreshold(state.intValue())) {
                context.db().save(entity);
                setAudioAlarmThreshold(state.intValue());
                refreshVideoMotionAlarmProviders();
            }
        }, true).setValue(new DecimalType(entity.getAudioThreshold()), false);
    }

    public synchronized @NotNull FFMPEG getOrCreateFfmpegHls(@NotNull StreamHLS.Resolution resolution) {
        return buildReStreamFFMPEG("HLS[%s]".formatted(resolution), () -> entity.buildHlsFfmpeg(resolution, this));
    }

    public synchronized @NotNull FFMPEG getOrCreateFfmpegDash() {
        return buildReStreamFFMPEG("DASH", () -> entity.buildDashFfmpeg(this));
    }

    @Override
    public void ffmpegLog(@NotNull Level level, @NotNull String message) {
        log.log(level, "[{}]: {}", entityID, message);
        updateLastSeen();
    }

    public synchronized @NotNull FFMPEG buildReStreamFFMPEG(@NotNull String description, Supplier<FFMPEG> createHandler) {
        if (ffmpegMainReStream == null || !ffmpegMainReStream.getDescription().equals(description)) {
            if (ffmpegMainReStream != null) {
                log.info("[{}]: FFMPEG - Stopping process: {}", entityID, ffmpegMainReStream.getDescription());
                ffmpegMainReStream.stopConverting(Duration.ofSeconds(10));
                log.info("[{}]: FFMPEG - Stopped successfully: {}", entityID, ffmpegMainReStream.getDescription());
            }
            ffmpegMainReStream = createHandler.get();
        }
        return ffmpegMainReStream;
    }

    private CameraDeviceEndpoint addEndpointOptional(@NotNull CameraConstants.AlarmEvent event) {
        if (!endpoints.containsKey(event.getEndpoint())) {
            addEndpointSwitch(event.getEndpoint(), state -> {
            }, false);
        }
        return endpoints.get(event.getEndpoint());
    }

    private void refreshVideoMotionAlarmProviders() {
        for (VideoMotionAlarmProvider provider : context.getBeansOfType(VideoMotionAlarmProvider.class)) {
            provider.entityUpdated(entity);
        }
    }
}

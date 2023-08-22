package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.CameraController.camerasOpenStreams;
import static org.homio.addon.camera.VideoConstants.CHANNEL_AUDIO_ALARM;
import static org.homio.addon.camera.VideoConstants.CHANNEL_LAST_MOTION_TYPE;
import static org.homio.addon.camera.VideoConstants.CHANNEL_START_STREAM;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_MOTION_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.MOTION_ALARM;
import static org.homio.addon.camera.service.util.VideoUrls.getCorrectUrlFormat;
import static org.homio.api.EntityContextMedia.CHANNEL_FFMPEG_MOTION_ALARM;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.INITIALIZE;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.REQUIRE_AUTH;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.CameraController.OpenStreamsContainer;
import org.homio.addon.camera.ConfigurationException;
import org.homio.addon.camera.OpenStreams;
import org.homio.addon.camera.entity.AbilityToStreamHLSOverFFMPEG;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.entity.VideoActionsContext;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.service.util.FFMpegRtspAlarm;
import org.homio.addon.camera.service.util.VideoUrls;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.addon.camera.ui.UIVideoEndpointAction;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.MimeTypeUtils;

@SuppressWarnings({"unused"})
@Log4j2
public abstract class BaseVideoService<T extends BaseVideoEntity<T, S>, S extends BaseVideoService<T, S>>
    extends EntityService.ServiceInstance<T> implements VideoActionsContext<T>,
    FFMPEGHandler {

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
        new ConfigDeviceDefinitionService("camera-devices.json");

    @Getter
    private final @NotNull Map<String, VideoDeviceEndpoint> endpoints = new ConcurrentHashMap<>();

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        return CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(entity.getModel(), Set.of());
    }

    protected abstract void updateNotificationBlock();

    protected abstract String getFFMPEGInputOptions(@Nullable String profile);

    protected abstract boolean pingCamera();

    protected abstract void dispose0();

    protected abstract void postInitializeCamera();

    protected @NotNull Map<String, ChannelTracking> channelTrackingMap = new ConcurrentHashMap<>();

    private @Getter Path ffmpegOutputPath;
    private @Getter Path ffmpegGifOutputPath;
    private @Getter Path ffmpegMP4OutputPath;
    private @Getter Path ffmpegHLSOutputPath;
    private @Getter Path ffmpegImageOutputPath;

    private @Getter final Map<String, State> attributes = new ConcurrentHashMap<>();
    private @Getter final Map<String, State> requestAttributes = new ConcurrentHashMap<>();

    private final FFMpegRtspAlarm ffMpegRtspAlarm;

    protected ReentrantLock lockCurrentSnapshot = new ReentrantLock();
    protected byte[] latestSnapshot = new byte[0];
    protected @Getter boolean motionDetected;

    protected @Getter FFMPEG ffmpegHLS;
    protected @Getter FFMPEG ffmpegSnapshot;
    protected @Getter FFMPEG ffmpegMjpeg;

    // run every 8 seconds to send requests to camera, etc...
    protected @Nullable ThreadContext<Void> pollCameraJob;
    // used to try to connect to camera
    protected @Nullable ThreadContext<Void> cameraConnectionJob;
    // scheduler to get snapshots
    protected @Nullable ThreadContext<Void> snapshotJob;

    // actions holder
    protected @Nullable UIInputBuilder uiInputBuilder;

    protected String snapshotInputOptions;
    protected String mp4OutOptions;
    protected String gifOutOptions;

    protected long videoStreamParametersHashCode;

    protected Instant lastSnapshotRequest = Instant.now();
    protected @Getter Instant currentSnapshotTime = Instant.now();

    protected @Setter boolean streamingSnapshotMjpeg = false;
    protected @Setter boolean streamingAutoFps = false;

    private @Getter
    @Setter
    @NotNull String mjpegContentType = "";
    public final VideoUrls urls = new VideoUrls();

    public BaseVideoService(T entity, EntityContext entityContext) {
        super(entityContext, entity, true);
        ffMpegRtspAlarm = new FFMpegRtspAlarm(entityContext, entity);
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer());
    }

    private void createConnectionJob() {
        cameraConnectionJob = entityContext
            .bgp().builder("video-connect-" + entityID)
            .intervalWithDelay(Duration.ofSeconds(30))
            .execute(() -> {
                try {
                    pollCameraConnection();
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

    protected void pollCameraConnection() throws Exception {
        keepMjpegRunning();
        recordImageSync(null);
        bringCameraOnline();
    }

    protected void keepMjpegRunning() {
        OpenStreamsContainer openStreams = camerasOpenStreams.get(entityID);
        if (!openStreams.openStreams.isEmpty()) {
            String mjpegUri = urls.getMjpegUri();
            if (!mjpegUri.isEmpty() && !"ffmpeg".equals(mjpegUri)) {
                openStreams.openStreams.queueFrame(("--" + openStreams.openStreams.boundary + "\r\n\r\n").getBytes());
            }
            openStreams.openStreams.queueFrame(getSnapshot());
        }
    }

    public void cameraCommunicationError(String reason) {
        updateEntityStatus(ERROR, reason);
        resetAndRetryConnecting();
    }

    protected void resetAndRetryConnecting() {
        offline();
        createConnectionJob();
    }

    @Override
    public void destroy() {
        dispose();
        deleteDirectories();
    }

    public final void initializeCamera() {
        this.urls.clear();
        createOrUpdateDeviceGroup();
        addPrimaryEndpoint();
        updateEntityStatus(INITIALIZE, null);
        log.info("[{}]: Initialize camera: <{}>", entityID, getEntity());
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer());
        videoStreamParametersHashCode = entity.getVideoParametersHashCode();

        try {
            this.urls.setRtspUri(entity.getRtspUri());
            this.urls.setMjpegUri(getCorrectUrlFormat(entity.getMjpegUrl()));
            this.urls.setSnapshotUri(getCorrectUrlFormat(entity.getSnapshotUrl()));

            postInitializeCamera();

            recreateFFmpeg();

            createConnectionJob();
        } catch (Exception ex) {
            disposeAndSetStatus(Status.ERROR, CommonUtils.getErrorMessage(ex));
        }
    }

    public final void disposeAndSetStatus(@NotNull Status status, @Nullable String reason) {
        if (updateEntityStatus(status, reason)) {
            log.warn("[{}]: Set video <{}> to status <{}>. Msg: <{}>", entityID, entity, status, reason);
            resetAndRetryConnecting();

            if (status == Status.ERROR || status == REQUIRE_AUTH) {
                entityContext.ui().sendErrorMessage("DISPOSE_VIDEO",
                    FlowMap.of("TITLE", entity.getTitle(), "REASON", reason));
            }
        }
    }

    private boolean updateEntityStatus(Status status, String reason) {
        if (entity.getStatus() != status || !Objects.equals(reason, entity.getStatusMessage())) {
            entity.setStatus(status, reason);
            VideoDeviceEndpoint endpoint = getEndpoints().get(ENDPOINT_DEVICE_STATUS);
            if (endpoint != null) {
                endpoint.setValue(new StringType(status.toString()), false);
            }
            // fully update entity due status/message/etc.. may be changed
            entityContext.ui().updateItem(entity);
            updateNotificationBlock();
            return true;
        }
        return false;
    }

    public final void bringCameraOnline() {
        updateLastSeen();
        if (!entity.getStatus().isOnline()) {
            setAttribute("URLS", new JsonType(OBJECT_MAPPER.convertValue(urls, ObjectNode.class)));
            updateEntityStatus(ONLINE, null);
            onCameraConnected();
            if (EntityContextBGP.cancel(cameraConnectionJob)) {
                cameraConnectionJob = null;
            }

            pollCameraJob = entityContext.bgp().builder("video-poll-" + entityID)
                                         .delay(Duration.ofSeconds(1))
                                         .interval(Duration.ofSeconds(8))
                                         .execute(this::pollCameraRunnable);

            // auto restart mjpeg stream now camera is back online.
            OpenStreams openStreams = camerasOpenStreams.get(entityID).openStreams;
            if (!openStreams.isEmpty()) {
                openCamerasStream();
            }
        }
    }

    protected void onCameraConnected() {
    }

    public void openCamerasStream() {
        if (urls.getMjpegUri().equals("ffmpeg")) {
            ffmpegMjpeg.startConverting();
        }
    }

    public UIInputBuilder assembleActions() {
        if (this.uiInputBuilder == null) {
            this.uiInputBuilder = entityContext.ui().inputBuilder();
            assembleAdditionalVideoActions(uiInputBuilder);
        }
        return uiInputBuilder;
    }

    @Override
    public State getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttributeRequest(String key, State state) {
        requestAttributes.put(key, state);
    }

    public void deleteDirectories() {
        CommonUtils.deletePath(ffmpegGifOutputPath);
        CommonUtils.deletePath(ffmpegMP4OutputPath);
        CommonUtils.deletePath(ffmpegImageOutputPath);
    }

    public void scheduleRequestSnapshot() {
        if ("ffmpeg".equals(urls.getSnapshotUri())) {
            FFMPEG.run(ffmpegSnapshot, FFMPEG::startConverting);
        } else {
            requestSnapshotByUri();
        }
    }

    public void startMJPEGRecord() {
        FFMPEG.run(ffmpegMjpeg, FFMPEG::startConverting);
    }

    public String getFFMPEGInputOptions() {
        return getFFMPEGInputOptions(null);
    }

    //@UIVideoAction(name = CHANNEL_START_STREAM, icon = "fas fa-expand-arrows-alt")
    public void startStream(boolean on) {
        FFMPEG localHLS;
        // this.ffmpegHLSStarted = on;
        if (on) {
            localHLS = ffmpegHLS;
            FFMPEG.run(localHLS, ffmpeg -> {
                ffmpeg.setKeepAlive(-1);// Now will run till manually stopped.
                if (ffmpeg.startConverting()) {
                    setAttribute(CHANNEL_START_STREAM, OnOffType.ON);
                }
            });
        } else {
            // Still runs but will be able to auto stop when the HLS stream is no longer used.
            FFMPEG.run(ffmpegHLS, ffmpeg -> ffmpeg.setKeepAlive(1));
        }
    }

    @UIVideoActionGetter(ENDPOINT_AUDIO_THRESHOLD)
    public DecimalType getAudioAlarmThreshold() {
        return new DecimalType(entity.getAudioThreshold());
    }

    @UIVideoActionGetter(ENDPOINT_MOTION_THRESHOLD)
    public DecimalType getMotionThreshold() {
        return new DecimalType(entity.getMotionThreshold());
    }

    public void setAttribute(String key, State state) {
        attributes.put(key, state);
        VideoDeviceEndpoint endpoint = endpoints.get(key);
        if (endpoint != null) {
            endpoint.setValue(state, true);
        }
        entityContext.event().fireEventIfNotSame(key + ":" + entityID, state);
    }

    @Override
    public void motionDetected(boolean on, String key) {
        if (on) {
            setAttribute(CHANNEL_LAST_MOTION_TYPE, new StringType(key));
        }
        setAttribute(key, OnOffType.of(on));
        setAttribute(MOTION_ALARM, OnOffType.of(on));
        motionDetected = on;
    }

    @Override
    public void audioDetected(boolean on) {
        setAttribute(CHANNEL_AUDIO_ALARM, OnOffType.of(on));
    }

    public void processSnapshot(byte[] incomingSnapshot) {
        log.debug("[{}]: Gеt video snapshot: <{}>", getEntityID(), getEntity());
        lockCurrentSnapshot.lock();
        try {
            if (!Arrays.equals(latestSnapshot, incomingSnapshot)) {
                latestSnapshot = incomingSnapshot;
                entityContext.ui().updateItem(getEntity(), "snapshot", latestSnapshot);
            }
        } finally {
            updateLastSeen();
            currentSnapshotTime = Instant.now();
            lockCurrentSnapshot.unlock();
        }
    }

    public void ffmpegError(String error) {
        log.error("[{}]: FFMPEG error: {}", entityID, error);
        entityContext.ui().sendErrorMessage("FFMPEG error: ''" + entityID + ". " + error);
    }

    @SneakyThrows
    public final RawType recordImageSync(String profile) {
        Path output = getFfmpegImageOutputPath().resolve("tmp_" + System.currentTimeMillis() + ".jpg");
        Files.createFile(output);
        takeSnapshotSync(profile, output);
        latestSnapshot = Files.readAllBytes(output);
        updateLastSeen();
        return new RawType(latestSnapshot, MimeTypeUtils.IMAGE_JPEG_VALUE, output.toString());
    }

    protected void takeSnapshotSync(@Nullable String profile, Path output) {
        fireFfmpegSync(profile, output, snapshotInputOptions, entity.getSnapshotOutOptionsAsString(), 20);
    }

    @UIVideoEndpointAction(ENDPOINT_AUDIO_THRESHOLD)
    public void setAudioThreshold(int threshold) {
        entityContext.updateDelayed(getEntity(), e -> e.setAudioThreshold(threshold));
        setAudioAlarmThreshold(threshold);
    }

    protected void setAudioAlarmThreshold(int threshold) {
        setAttribute(ENDPOINT_AUDIO_THRESHOLD, new StringType(threshold));
        if (threshold == 0) {
            audioDetected(false);
        }
    }

    @Override
    protected void firstInitialize() {
        ffmpegOutputPath = CommonUtils.getMediaPath().resolve(entity.getFolderName()).resolve(entityID);
        ffmpegImageOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("images"));
        ffmpegGifOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("gif"));
        ffmpegMP4OutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("mp4"));
        ffmpegHLSOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("hls"));
        try {
            FileUtils.cleanDirectory(ffmpegHLSOutputPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Unable to clean path: " + ffmpegHLSOutputPath);
        }
        initialize();
    }

    @UIVideoEndpointAction(ENDPOINT_MOTION_THRESHOLD)
    public void setMotionThreshold(int threshold) {
        entityContext.updateDelayed(getEntity(), e -> e.setMotionThreshold(threshold));
        setMotionAlarmThreshold(threshold);
    }

    public void startOrAddMotionAlarmListener(String listener) {
        if (!isMotionAlarmHandlesByVideo()) {
            ffMpegRtspAlarm.addMotionAlarmListener(listener);
        }
    }

    public void removeMotionAlarmListener(String listener) {
        if (!isMotionAlarmHandlesByVideo()) {
            ffMpegRtspAlarm.removeMotionAlarmListener(listener);
        }
    }

    @SneakyThrows
    public byte[] recordGifSync(String profile, int secondsToRecord) {
        Path output = getFfmpegGifOutputPath().resolve("tmp_" + System.currentTimeMillis() + ".gif");
        fireFfmpegSync(profile, output, "-y -t " + secondsToRecord + " -hide_banner -loglevel warning",
            gifOutOptions, secondsToRecord + 20);
        return Files.readAllBytes(output);
    }

    @SneakyThrows
    public byte[] recordMp4Sync(String profile, int secondsToRecord) {
        Path output = getFfmpegMP4OutputPath().resolve("tmp_" + System.currentTimeMillis() + ".mp4");
        fireFfmpegSync(profile, output, "-y -t " + secondsToRecord + " -hide_banner -loglevel warning",
            mp4OutOptions, secondsToRecord + 20);
        return Files.readAllBytes(output);
    }

    @Override
    @SneakyThrows
    protected final void initialize() {
        if (!entity.isStart()) {
            dispose();
        } else if (videoStreamParametersHashCode != entity.getVideoParametersHashCode()) {
            dispose();
            initializeCamera();
        }
    }

    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {

    }

    public byte[] getSnapshot() {
        if (!entity.isStart() || !entity.getStatus().isOnline()) {
            // Single gray pixel JPG to keep streams open when the camera goes offline, so they don't stop.
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
        if (snapshotJob == null && !ffmpegSnapshot.isRunning() && lastUpdatedMs >= Duration.ofSeconds(60).toMillis()) {
            scheduleRequestSnapshot();
        }
        lockCurrentSnapshot.lock();
        try {
            return latestSnapshot;
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    public void startSnapshotPolling() {
        if (snapshotJob != null || urls.getSnapshotUri().equals("ffmpeg")) {
            return; // Already polling or creating with FFmpeg from RTSP
        }
        if (streamingSnapshotMjpeg || streamingAutoFps) {
            snapshotJob = entityContext.bgp()
                                       .builder(entity.getTitle() + " SnapshotJob")
                                       .delay(Duration.ofMillis(200))
                                       .interval(Duration.ofSeconds(entity.getSnapshotPollInterval()))
                                       .execute(this::requestSnapshotByUri);
        }
    }

    protected void setMotionAlarmThreshold(int threshold) {
        setAttribute(ENDPOINT_MOTION_THRESHOLD, new StringType(threshold));
        if (threshold == 0) {
            motionDetected(false, CHANNEL_FFMPEG_MOTION_ALARM);
        }
    }

    protected boolean isAudioAlarmHandlesByVideo() {
        return false;
    }

    protected boolean isMotionAlarmHandlesByVideo() {
        return false;
    }

    protected boolean hasAudioStream() {
        return entity.isHasAudioStream();
    }

    protected void offline() {
        if (EntityContextBGP.cancel(pollCameraJob)) {
            pollCameraJob = null;
        }
        if (EntityContextBGP.cancel(cameraConnectionJob)) {
            cameraConnectionJob = null;
        }
        if (EntityContextBGP.cancel(snapshotJob)) {
            snapshotJob = null;
        }

        FFMPEG.run(ffmpegHLS, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegMjpeg, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegSnapshot, FFMPEG::stopConverting);

        ffMpegRtspAlarm.stop();
    }

    private synchronized void dispose() {
        log.info("[{}]: Dispose video: <{}>", entityID, getEntity());
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

    @SneakyThrows
    private void fireFfmpegSync(String profile, Path output, String inputArguments, String outOptions, int maxTimeout) {
        entityContext.media().fireFfmpeg(
            inputArguments + " " + getFFMPEGInputOptions(profile),
            urls.getSnapshotUri(profile),
            outOptions + " " + output,
            maxTimeout);
    }

    private String buildHlsOptions() {
        AbilityToStreamHLSOverFFMPEG hlsOptions = (AbilityToStreamHLSOverFFMPEG) getEntity();
        List<String> options = new ArrayList<>();
        options.add("-strict -2");
        options.add("-c:v " + hlsOptions.getVideoCodec()); // video codec
        options.add("-hls_flags delete_segments"); // remove old segments
        options.add("-hls_init_time 1"); // build first ts ASAP
        options.add("-hls_time 2"); // ~ 2sec per file ?
        options.add("-hls_list_size " + hlsOptions.getHlsListSize()); // how many files
        if (isNotEmpty(hlsOptions.getHlsScale())) {
            options.add("-vf scale=" + hlsOptions.getHlsScale()); // scale result video
        }
        if (hasAudioStream()) {
            options.add("-c:a " + hlsOptions.getAudioCodec());
            options.add("-ac 2"); // audio channels (stereo)
            options.add("-ab 32k"); // audio bitrate in Kb/s
            options.add("-ar 44100"); // audio sampling rate
        }
        options.addAll(hlsOptions.getExtraOptions());
        return String.join(" ", options);
    }

    protected void pollCameraRunnable() {
        FFMPEG.run(ffmpegHLS, FFMPEG::stopProcessIfNoKeepAlive);
        FFMPEG.run(ffmpegMjpeg, FFMPEG::restartIfRequire);
        if (snapshotJob == null) {
            checkCameraConnection();
        }
    }

    private void checkCameraConnection() {
        if (snapshotJob != null) {// Currently polling a real URL for snapshots, so camera must be online.
            return;
        } else if (ffmpegSnapshot.isRunning()) {// Use RTSP stream creating snapshots to know camera is online.
            if (!ffmpegSnapshot.getIsAlive()) {
                cameraCommunicationError("FFmpeg Snapshots Stopped: Check your camera can be reached.");
                return;
            }
            return;// ffmpeg snapshot stream is still alive
        }
        if (!pingCamera()) {
            cameraCommunicationError(
                "Connection Timeout: Check your IP and PORT are correct and the camera can be reached.");
        }
    }

    public void stopSnapshotPolling() {
        if (!streamingSnapshotMjpeg) {
            if (EntityContextBGP.cancel(snapshotJob)) {
                snapshotJob = null;
            }
        }
    }

    protected void requestSnapshotByUri() {
        throw new IllegalStateException("Target service must override this method if snapshotUri not empty");
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

    public final void recordMp4(@NotNull Path filePath, @Nullable String profile, int secondsToRecord) {
        String inputOptions = getFFMPEGInputOptions(profile);
        inputOptions = "-y -t " + secondsToRecord + " -hide_banner -loglevel warning " + inputOptions;
        FFMPEG ffmpegMP4 = entityContext
            .media()
            .buildFFMPEG(entityID, "FFMPEG record MP4", this, log, FFMPEGFormat.RECORD, inputOptions,
                urls.getRtspUri(profile),
                mp4OutOptions, filePath.toString(),
                entity.getUser(), entity.getPassword().asString(), null);
        FFMPEG.run(ffmpegMP4, FFMPEG::startConverting);
    }

    public final void recordGif(Path filePath, @Nullable String profile, int secondsToRecord) {
        String gifInputOptions = "-y -t " + secondsToRecord + " -hide_banner -loglevel warning " + getFFMPEGInputOptions();
        FFMPEG ffmpegGIF = entityContext.media().buildFFMPEG(entityID, "FFMPEG GIF", this, log, FFMPEGFormat.GIF,
            gifInputOptions, urls.getRtspUri(profile),
            gifOutOptions, filePath.toString(), entity.getUser(),
            entity.getPassword().asString(), null);
        FFMPEG.run(ffmpegGIF, FFMPEG::startConverting);
    }

    public void updateLastSeen() {
        endpoints.get(ENDPOINT_LAST_SEEN).setValue(new DecimalType(System.currentTimeMillis()), true);
    }

    void recreateFFmpeg() {
        FFMPEG.run(ffmpegMjpeg, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegSnapshot, FFMPEG::stopConverting);
        FFMPEG.run(ffmpegHLS, FFMPEG::stopConverting);

        this.snapshotInputOptions = getFFMPEGInputOptions() + " -threads 1 -skip_frame nokey -hide_banner -loglevel warning -an";
        this.mp4OutOptions = String.join(" ", entity.getMp4OutOptions());
        this.gifOutOptions = String.join(" ", entity.getGifOutOptions());

        String rtspUri = urls.getRtspUri();
        ffmpegMjpeg = entityContext.media().buildFFMPEG(entityID, "FFMPEG mjpeg", this, log,
            FFMPEGFormat.MJPEG, getFFMPEGInputOptions() + " -hide_banner -loglevel warning", rtspUri,
            String.join(" ", entity.getMjpegOutOptions()), entity.getUrl("ipcamera.jpg"),
            entity.getUser(), entity.getPassword().asString(), null);
        setAttribute("FFMPEG_MJPEG", new StringType(String.join(" ", ffmpegMjpeg.getCommandArrayList())));

        ffmpegSnapshot = entityContext.media().buildFFMPEG(entityID, "FFMPEG snapshot", this, log,
            FFMPEGFormat.SNAPSHOT, snapshotInputOptions, rtspUri,
            entity.getSnapshotOutOptionsAsString(),
            entity.getUrl("snapshot.jpg"),
            entity.getUser(), entity.getPassword().asString(), null);
        setAttribute("FFMPEG_SNAPSHOT", new StringType(String.join(" ", ffmpegSnapshot.getCommandArrayList())));

        if (entity instanceof AbilityToStreamHLSOverFFMPEG hlsOverFFMPEG) {
            ffmpegHLS = entityContext.media().buildFFMPEG(entityID, "FFMPEG HLS", this, log, FFMPEGFormat.HLS,
                "-hide_banner -loglevel warning " + getFFMPEGInputOptions(),
                StringUtils.defaultString(hlsOverFFMPEG.getHlsRtspUri(), rtspUri),
                buildHlsOptions(), getFfmpegHLSOutputPath().resolve("ipcamera.m3u8").toString(),
                entity.getUser(), entity.getPassword().asString(),
                () -> setAttribute(CHANNEL_START_STREAM, OnOffType.OFF));
            setAttribute("FFMPEG_HLS", new StringType(String.join(" ", ffmpegHLS.getCommandArrayList())));
        }
    }

    private void createOrUpdateDeviceGroup() {
        List<ConfigDeviceDefinition> devices = findDevices();
        Icon icon = new Icon(
            CONFIG_DEVICE_SERVICE.getDeviceIcon(devices, "fas fa-video"),
            CONFIG_DEVICE_SERVICE.getDeviceIconColor(devices, UI.Color.random())
        );
        entityContext.var().createGroup("video", "Video", true, new Icon("fas fa-video", "#0E578F"));
        entityContext.var().createGroup("video", entity.getGroupID(), entity.getDeviceFullName(), true,
            icon, getGroupDescription());
    }

    private String getGroupDescription() {
        if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
            return entity.getIeeeAddress();
        }
        return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
    }

    private void addPrimaryEndpoint() {
        endpoints.computeIfAbsent(ENDPOINT_DEVICE_STATUS, key -> {
            Set<String> range = Status.set(ONLINE, ERROR, OFFLINE, REQUIRE_AUTH, UNKNOWN, INITIALIZE);
            VideoDeviceEndpoint videoEndpoint = new VideoDeviceEndpoint(entity, key, range) {
                @Override
                public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
                    Status status = Status.valueOf(getValue().stringValue());
                    uiInputBuilder.addInfo(status.name(), InfoType.Text).setColor(status.getColor());
                    super.assembleUIAction(uiInputBuilder);
                }
            };
            videoEndpoint.writeValue(INITIALIZE.toString(), false);
            return videoEndpoint;
        });

        endpoints.computeIfAbsent(ENDPOINT_LAST_SEEN, key -> {
            VideoDeviceEndpoint videoEndpoint = new VideoDeviceEndpoint(entity, key, EndpointType.number, false) {

                @Override
                public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
                    uiInputBuilder.addDuration(getValue().longValue(), null);
                }
            };
            videoEndpoint.writeValue(System.currentTimeMillis(), false);
            return videoEndpoint;
        });

        endpoints.computeIfAbsent(ENDPOINT_MOTION_THRESHOLD, key ->
            new VideoDeviceEndpoint(entity, key, 0F, 100F, true));

        if (entity.isHasAudioStream()) {
            endpoints.computeIfAbsent(ENDPOINT_AUDIO_THRESHOLD, key ->
                new VideoDeviceEndpoint(entity, key, 0F, 100F, true));
        }
    }

    public VideoDeviceEndpoint addEndpoint(String endpointId, Function<String, VideoDeviceEndpoint> handler, Consumer<State> updateHandler) {
        return endpoints.computeIfAbsent(endpointId, key -> {
            VideoDeviceEndpoint endpoint = handler.apply(key);
            endpoint.setUpdateHandler(updateHandler);
            return endpoint;
        });
    }

    public VideoDeviceEndpoint addEndpointSwitch(String endpointId, Consumer<State> updateHandler, boolean writable) {
        return addEndpoint(endpointId, key -> new VideoDeviceEndpoint(entity, key, EndpointType.bool, writable), updateHandler);
    }

    public VideoDeviceEndpoint addEndpointEnum(String endpointId, Set<String> range, Consumer<State> updateHandler) {
        return addEndpoint(endpointId, key -> new VideoDeviceEndpoint(entity, key, range), updateHandler);
    }

    public VideoDeviceEndpoint addEndpointSlider(String endpointId, int min, int max, Consumer<State> updateHandler, boolean writable) {
        return addEndpoint(endpointId, key -> new VideoDeviceEndpoint(entity, key, (float) min, (float) max, writable), updateHandler);
    }
}

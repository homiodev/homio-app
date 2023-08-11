package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.CameraController.camerasOpenStreams;
import static org.homio.addon.camera.VideoConstants.CHANNEL_AUDIO_ALARM;
import static org.homio.addon.camera.VideoConstants.CHANNEL_AUDIO_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.CHANNEL_LAST_MOTION_TYPE;
import static org.homio.addon.camera.VideoConstants.CHANNEL_MOTION_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.CHANNEL_START_STREAM;
import static org.homio.addon.camera.VideoConstants.MOTION_ALARM;
import static org.homio.addon.camera.service.util.VideoUrls.getCorrectUrlFormat;
import static org.homio.api.EntityContextMedia.CHANNEL_FFMPEG_MOTION_ALARM;
import static org.homio.api.model.Status.ERROR;
import static org.homio.api.model.Status.REQUIRE_AUTH;
import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.ObjectType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
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
    protected @Getter byte[] latestSnapshot = new byte[0];
    protected @Getter long lastAnswerFromVideo;
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

    private @Getter @Setter @NotNull String mjpegContentType = "";
    protected final VideoUrls urls = new VideoUrls();

    public BaseVideoService(T entity, EntityContext entityContext) {
        super(entityContext, entity, true);
        ffMpegRtspAlarm = new FFMpegRtspAlarm(entityContext, entity);
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer());
    }

    private void tryConnecting() {
        cameraConnectionJob = entityContext
            .bgp().builder("video-connect-" + entityID)
            .intervalWithDelay(Duration.ofSeconds(8))
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
                        entity.setStatus(Status.OFFLINE, message);
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
        // will try to reconnect again as camera may be rebooting.
        boolean wasOnline = entity.getStatus().isOnline();
        entity.setStatus(Status.ERROR, reason);
        entityUpdated();
        if (wasOnline) {
            resetAndRetryConnecting();
        }
    }

    protected void resetAndRetryConnecting() {
        offline();
        tryConnecting();
    }

    @Override
    public void destroy() {
        dispose();
        deleteDirectories();
    }

    public final void initializeCamera() {
        this.urls.clear();
        log.info("[{}]: Initialize camera: <{}>", entityID, getEntity());
        camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer());
        videoStreamParametersHashCode = entity.getVideoParametersHashCode();
        try {
            if (!entity.getEntityID().equals(entityID)) {
                throw new RuntimeException(
                    "Unable to init video <" + getEntity() + "> with different id than: " + entityID);
            }

            this.urls.setRtspUri(entity.getRtspUri());
            this.urls.setMjpegUri(getCorrectUrlFormat(entity.getMjpegUrl()));
            this.urls.setSnapshotUri(getCorrectUrlFormat(entity.getRawSnapshotUrl()));

            this.snapshotInputOptions = getFFMPEGInputOptions() + " -threads 1 -skip_frame nokey -hide_banner -loglevel warning -an";
            this.mp4OutOptions = String.join(" ", entity.getMp4OutOptions());
            this.gifOutOptions = String.join(" ", entity.getGifOutOptions());
            String mgpegOutOptions = String.join(" ", entity.getMjpegOutOptions());

            String rtspUri = urls.getRtspUri();
            ffmpegMjpeg = entityContext.media().buildFFMPEG(entityID, "FFMPEG mjpeg", this, log,
                FFMPEGFormat.MJPEG, getFFMPEGInputOptions() + " -hide_banner -loglevel warning", rtspUri,
                mgpegOutOptions, entity.getUrl("ipcamera.jpg"),
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

            postInitializeCamera();

            tryConnecting();
        } catch (Exception ex) {
            disposeAndSetStatus(Status.ERROR, CommonUtils.getErrorMessage(ex));
        }
    }

    private boolean isRequireRestart() {
        return !entity.getStatus().isOnline() || videoStreamParametersHashCode != entity.getVideoParametersHashCode();
    }

    public final void disposeAndSetStatus(@NotNull Status status, @Nullable String reason) {
        if (entity.getStatus() != status || !Objects.equals(reason, entity.getStatusMessage())) {
            log.warn("[{}]: Set video <{}> to status <{}>. Msg: <{}>", entityID, entity, status, reason);
            dispose();

            if (status == Status.ERROR || status == REQUIRE_AUTH) {
                entityContext.ui().sendErrorMessage("DISPOSE_VIDEO",
                    FlowMap.of("TITLE", entity.getTitle(), "REASON", reason));
            }
            entity.setStatus(status, reason);
            if (entity.isStart()) {
                entityContext.save(entity.setStart(false), false);
            }
            entityUpdated();
        }
    }

    private void entityUpdated() {
        entityContext.ui().updateItem(entity);
        updateNotificationBlock();
    }

    public final void bringCameraOnline() {
        log.debug("Bring camera online");
        setAttribute("URLS", new ObjectType(urls));
        lastAnswerFromVideo = System.currentTimeMillis();
        if (!entity.getStatus().isOnline()) {
            setAttribute("URLS", new JsonType(OBJECT_MAPPER.convertValue(urls, ObjectNode.class)));
            entity.setStatusOnline();
            cameraConnected();
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
            entityUpdated();
        }
    }

    protected void cameraConnected() {
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

    @UIVideoActionGetter(CHANNEL_AUDIO_THRESHOLD)
    public DecimalType getAudioAlarmThreshold() {
        return new DecimalType(entity.getAudioThreshold());
    }

    @UIVideoActionGetter(CHANNEL_MOTION_THRESHOLD)
    public DecimalType getMotionThreshold() {
        return new DecimalType(entity.getMotionThreshold());
    }

    public void setAttribute(String key, State state) {
        attributes.put(key, state);
        entityContext.event().fireEventIfNotSame(key + ":" + entityID, state);

        if (key.equals(CHANNEL_AUDIO_THRESHOLD)) {
            entityContext.updateDelayed(getEntity(), e -> e.setAudioThreshold(state.intValue()));
        } else if (key.equals(CHANNEL_MOTION_THRESHOLD)) {
            entityContext.updateDelayed(getEntity(), e -> e.setMotionThreshold(state.intValue()));
        }
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
                entityContext.ui().updateItem(getEntity(), "lastSnapshot", latestSnapshot);
            }
        } finally {
            lastAnswerFromVideo = System.currentTimeMillis();
            currentSnapshotTime = Instant.now();
            lockCurrentSnapshot.unlock();
        }
    }

    public void ffmpegError(String error) {
        log.error("[{}]: FFMPEG error: {}", entityID, error);
        entityContext.ui().sendErrorMessage("FFMPEG error: ''" + entityID + ". " + error);
    }

    public RawType recordImageSync(String profile) {
        String output = getFfmpegImageOutputPath().resolve("tmp_" + System.currentTimeMillis() + ".jpg").toString();
        byte[] imageBytes =
            fireFfmpegSync(profile, output, snapshotInputOptions, entity.getSnapshotOutOptionsAsString(), 20);
        latestSnapshot = imageBytes;
        lastAnswerFromVideo = System.currentTimeMillis();
        return new RawType(imageBytes, MimeTypeUtils.IMAGE_JPEG_VALUE);
    }

    protected void setAudioAlarmThreshold(int threshold) {
        setAttribute(CHANNEL_AUDIO_THRESHOLD, new StringType(threshold));
        if (threshold == 0) {
            audioDetected(false);
        }
    }

    @UIVideoAction(name = CHANNEL_AUDIO_THRESHOLD, order = 120, icon = "fas fa-volume-up", type = UIVideoAction.ActionType.Dimmer)
    public void setAudioThreshold(int threshold) {
        entityContext.updateDelayed(getEntity(), e -> e.setAudioThreshold(threshold));
        setAudioAlarmThreshold(threshold);
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

    @UIVideoAction(name = CHANNEL_MOTION_THRESHOLD, order = 110, icon = "fas fa-expand-arrows-alt",
                   type = UIVideoAction.ActionType.Dimmer, max = 1000)
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
        String output = getFfmpegGifOutputPath().resolve("tmp_" + System.currentTimeMillis() + ".gif").toString();
        return fireFfmpegSync(profile, output, "-y -t " + secondsToRecord + " -hide_banner -loglevel warning",
            gifOutOptions, secondsToRecord + 20);
    }

    public byte[] recordMp4Sync(String profile, int secondsToRecord) {
        String output = getFfmpegMP4OutputPath().resolve("tmp_" + System.currentTimeMillis() + ".mp4").toString();
        return fireFfmpegSync(profile, output, "-y -t " + secondsToRecord + " -hide_banner -loglevel warning",
            mp4OutOptions, secondsToRecord + 20);
    }

    @Override
    @SneakyThrows
    protected final void initialize() {
        if (!entity.isStart()) {
            this.disposeAndSetStatus(Status.OFFLINE, "Camera not started");
        } else {
            if (isRequireRestart()) {
                dispose();
                initializeCamera();
            }
        }
    }

    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {

    }

    public byte[] getSnapshot() {
        if (!entity.getStatus().isOnline()) {
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
        if (snapshotJob == null && !ffmpegSnapshot.isRunning() && lastUpdatedMs >= entity.getSnapshotPollInterval()) {
            requestSnapshotByUri();
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
        setAttribute(CHANNEL_MOTION_THRESHOLD, new StringType(threshold));
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
        entity.setStatus(Status.OFFLINE);
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

        camerasOpenStreams.remove(entityID).dispose();
        channelTrackingMap.clear();

        try {
            dispose0();
        } catch (Exception ex) {
            log.error("[{}]: Error while dispose video: <{}>", entityID, getEntity(), ex);
        }
    }

    @SneakyThrows
    private byte[] fireFfmpegSync(String profile, String output, String inputArguments, String outOptions, int maxTimeout) {
        Path path = Paths.get(output);
        try {
            Files.createFile(path);
            entityContext.media().fireFfmpeg(
                inputArguments + " " + getFFMPEGInputOptions(profile),
                urls.getSnapshotUri(profile),
                outOptions + " " + output,
                maxTimeout);
            return IOUtils.toByteArray(Files.newInputStream(path));
        } finally {
            try {
                Files.delete(path);
            } catch (IOException ex) {
                log.error("[{}]: Unable to remove file: <{}>", getEntityID(), output, ex);
            }
        }
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
}

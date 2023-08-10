package org.homio.addon.camera.service;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.VideoConstants.CHANNEL_AUDIO_ALARM;
import static org.homio.addon.camera.VideoConstants.CHANNEL_AUDIO_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.CHANNEL_LAST_MOTION_TYPE;
import static org.homio.addon.camera.VideoConstants.CHANNEL_MOTION_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.CHANNEL_START_STREAM;
import static org.homio.addon.camera.VideoConstants.MOTION_ALARM;
import static org.homio.api.EntityContextMedia.CHANNEL_FFMPEG_MOTION_ALARM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.homio.addon.camera.entity.AbilityToStreamHLSOverFFMPEG;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.entity.VideoActionsContext;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.state.DecimalType;
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

    public abstract String getRtspUri(String profile);

    protected abstract void updateNotificationBlock();

    protected abstract void testVideoOnline() throws Exception;

    protected abstract String getFFMPEGInputOptions(@Nullable String profile);

    private @Getter Path ffmpegOutputPath;
    private @Getter Path ffmpegGifOutputPath;
    private @Getter Path ffmpegMP4OutputPath;
    private @Getter Path ffmpegHLSOutputPath;
    private @Getter Path ffmpegImageOutputPath;

    private @Getter final Map<String, State> attributes = new ConcurrentHashMap<>();
    private @Getter final Map<String, State> requestAttributes = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Status>> stateListeners = new HashMap<>();
    private final FFMpegRtspAlarm ffMpegRtspAlarm = new FFMpegRtspAlarm();

    protected ReentrantLock lockCurrentSnapshot = new ReentrantLock();
    protected @Getter byte[] latestSnapshot = new byte[0];
    protected @Getter long lastAnswerFromVideo;
    protected @Getter boolean motionDetected;

    protected @Getter FFMPEG ffmpegHLS;
    protected FFMPEG ffmpegGIF;
    protected FFMPEG ffmpegSnapshot;
    protected @Getter @Setter FFMPEG ffmpegMjpeg;
    protected FFMPEG ffmpegMP4;

    protected @Getter boolean isHandlerInitialized;

    protected ThreadContext<Void> videoConnectionJob;
    protected ThreadContext<Void> pollVideoJob;
    protected ThreadContext<Void> cameraConnectionJob;

    // actions holder
    protected UIInputBuilder uiInputBuilder;

    protected String snapshotSource;
    protected String snapshotInputOptions;

    protected String mp4OutOptions;
    protected String gifOutOptions;

    protected long videoStreamParametersHashCode;

    protected Instant lastSnapshotRequest = Instant.now();
    protected @Getter Instant currentSnapshotTime = Instant.now();

    private @Setter @Nullable String overrideRtspUri;

    protected @Setter @Getter @NotNull String mjpegUri = "";
    protected @Setter @Getter @NotNull String mjpegContentType = "";
    protected @NotNull String snapshotUri = "";

    protected boolean updateAutoFps = false;
    protected @Getter boolean ffmpegSnapshotGeneration = false;
    protected boolean snapshotPolling = false;
    protected @Setter boolean streamingSnapshotMjpeg = false;
    protected @Setter boolean streamingAutoFps = false;

    public BaseVideoService(T entity, EntityContext entityContext) {
        super(entityContext, entity, true);
    }

    @Override
    public void destroy() {
        dispose();
        deleteDirectories();
    }

    public final void initializeVideo() {
        log.info("[{}]: Initialize video: <{}>", entityID, getEntity());
        videoStreamParametersHashCode = entity.getVideoParametersHashCode();
        isHandlerInitialized = true;
        try {
            if (!entity.getEntityID().equals(entityID)) {
                throw new RuntimeException(
                    "Unable to init video <" + getEntity() + "> with different id than: " + entityID);
            }

            initialize0();

            videoConnectionJob = entityContext.bgp().builder("poll-video-connection-" + entityID)
                                              .interval(Duration.ofSeconds(60)).execute(this::pollingVideoConnection);
            entity.setStatusOnline();
            entityUpdated();
        } catch (Exception ex) {
            disposeAndSetStatus(Status.ERROR, CommonUtils.getErrorMessage(ex));
        }
    }

    public final void recordMp4(Path filePath, @Nullable String profile, int secondsToRecord) {
        String inputOptions = getFFMPEGInputOptions(profile);
        inputOptions = "-y -t " + secondsToRecord + " -hide_banner -loglevel warning " + inputOptions;
        ffmpegMP4 =
            entityContext.media().buildFFMPEG(entityID, "FFMPEG record MP4", this, log, FFMPEGFormat.RECORD, inputOptions,
                getRtspUrlInternal(profile),
                mp4OutOptions, filePath.toString(),
                entity.getUser(), entity.getPassword().asString(), null);
        fireFfmpeg(ffmpegMP4, FFMPEG::startConverting);
    }

    private String getRtspUrlInternal(@Nullable String profile) {
        return overrideRtspUri == null ? getRtspUri(profile) : overrideRtspUri;
    }

    private boolean isRequireRestart() {
        return !isHandlerInitialized() || videoStreamParametersHashCode != entity.getVideoParametersHashCode();
    }

    public final void recordGif(Path filePath, @Nullable String profile, int secondsToRecord) {
        String gifInputOptions = "-y -t " + secondsToRecord + " -hide_banner -loglevel warning " + getFFMPEGInputOptions();
        ffmpegGIF = entityContext.media().buildFFMPEG(entityID, "FFMPEG GIF", this, log, FFMPEGFormat.GIF,
            gifInputOptions, getRtspUrlInternal(profile),
            gifOutOptions, filePath.toString(), entity.getUser(),
            entity.getPassword().asString(), null);
        fireFfmpeg(ffmpegGIF, FFMPEG::startConverting);
    }

    // synchronized to work properly with isHandlerInitialized
    public synchronized final void disposeAndSetStatus(Status status, String reason) {
        if (isHandlerInitialized) {
            // set it before to avoid recursively disposing from listeners
            log.warn("[{}]: Set video <{}> to status <{}>. Msg: <{}>", entityID, entity, status, reason);

            this.stateListeners.values().forEach(h -> h.accept(status));
            dispose();
        }
        if (status == Status.ERROR || status == Status.REQUIRE_AUTH) {
            entityContext.ui().sendErrorMessage("DISPOSE_VIDEO",
                FlowMap.of("TITLE", entity.getTitle(), "REASON", reason));
        }
        entity.setStatus(status, reason);
        if (entity.isStart()) {
            entityContext.save(entity.setStart(false), false);
        }
        entityUpdated();
    }

    private void entityUpdated() {
        entityContext.ui().updateItem(entity);
        updateNotificationBlock();
    }

    public final void bringCameraOnline() {
        entity.setStatusOnline();
        lastAnswerFromVideo = System.currentTimeMillis();
        if (pollVideoJob == null && isHandlerInitialized) {
            disposeVideoConnectionJob();
            pollVideoJob = entityContext.bgp().builder("poll-video-runnable-" + entityID)
                                        .intervalWithDelay(Duration.ofSeconds(8)).execute(this::pollCameraRunnable);
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

    public void addVideoChangeState(String key, Consumer<Status> handler) {
        this.stateListeners.put(key, handler);
    }

    public void removeVideoChangeState(String key) {
        this.stateListeners.remove(key);
    }

    public void startSnapshot() {
        fireFfmpeg(ffmpegSnapshot, FFMPEG::startConverting);
    }

    public void startMJPEGRecord() {
        fireFfmpeg(ffmpegMjpeg, FFMPEG::startConverting);
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
            fireFfmpeg(localHLS, ffmpeg -> {
                ffmpeg.setKeepAlive(-1);// Now will run till manually stopped.
                if (ffmpeg.startConverting()) {
                    setAttribute(CHANNEL_START_STREAM, OnOffType.ON);
                }
            });
        } else {
            // Still runs but will be able to auto stop when the HLS stream is no longer used.
            fireFfmpeg(ffmpegHLS, ffmpeg -> ffmpeg.setKeepAlive(1));
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
            lockCurrentSnapshot.unlock();
            currentSnapshotTime = Instant.now();
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
    protected void initialize() {
        if (!entity.isStart()) {
            // check maybe status already offline/error
            if (entity.getStatus().isOnline() || isHandlerInitialized) {
                this.disposeAndSetStatus(Status.OFFLINE, "Camera not started");
            }
        } else {
            try {
                if (isRequireRestart()) {
                    dispose();
                    testVideoOnline();
                    initializeVideo();
                }
            } catch (BadCredentialsException ex) {
                this.disposeAndSetStatus(Status.REQUIRE_AUTH, CommonUtils.getErrorMessage(ex));
            } catch (Exception ex) {
                this.disposeAndSetStatus(Status.ERROR, CommonUtils.getErrorMessage(ex));
            }
        }

        testVideoOnline();
    }

    @Override
    @SneakyThrows
    public void testService() {
        testVideoOnline();
    }

    protected void assembleAdditionalVideoActions(UIInputBuilder uiInputBuilder) {

    }

    public static void fireFfmpeg(FFMPEG ffmpeg, Consumer<FFMPEG> handler) {
        if (ffmpeg != null) {
            handler.accept(ffmpeg);
        }
    }

    protected void pollingVideoConnection() {
        startSnapshot();
    }

    protected void pollCameraRunnable() {
        fireFfmpeg(ffmpegHLS, FFMPEG::stopProcessIfNoKeepAlive);
        fireFfmpeg(ffmpegMjpeg, FFMPEG::restartIfRequire);

        long timePassed = System.currentTimeMillis() - lastAnswerFromVideo;
        if (timePassed > 1200000) { // more than 2 min passed
            disposeAndSetStatus(Status.OFFLINE, "More that 2 min without answer from source");
        } else if (timePassed > 30000) {
            startSnapshot();
        }
    }

    protected void initialize0() {
        this.snapshotSource = initSnapshotInput();
        T videoStreamEntity = getEntity();
        this.snapshotInputOptions = getFFMPEGInputOptions() + " -threads 1 -skip_frame nokey -hide_banner -loglevel warning -an";
        this.mp4OutOptions = String.join(" ", videoStreamEntity.getMp4OutOptions());
        this.gifOutOptions = String.join(" ", videoStreamEntity.getGifOutOptions());
        String mgpegOutOptions = String.join(" ", videoStreamEntity.getMjpegOutOptions());

        String rtspUri = getRtspUrlInternal(null);

        ffmpegMjpeg = entityContext.media().buildFFMPEG(entityID, "FFMPEG mjpeg", this, log,
            FFMPEGFormat.MJPEG, getFFMPEGInputOptions() + " -hide_banner -loglevel warning", rtspUri,
            mgpegOutOptions, entity.getUrl("ipcamera.jpg"),
            videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(), null);
        setAttribute("FFMPEG_MJPEG", new StringType(String.join(" ", ffmpegMjpeg.getCommandArrayList())));

        ffmpegSnapshot = entityContext.media().buildFFMPEG(entityID, "FFMPEG snapshot", this, log,
            FFMPEGFormat.SNAPSHOT, snapshotInputOptions, rtspUri,
            videoStreamEntity.getSnapshotOutOptionsAsString(),
            entity.getUrl("snapshot.jpg"),
            videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(), null);
        setAttribute("FFMPEG_SNAPSHOT", new StringType(String.join(" ", ffmpegSnapshot.getCommandArrayList())));

        if (videoStreamEntity instanceof AbilityToStreamHLSOverFFMPEG) {
            ffmpegHLS = entityContext.media().buildFFMPEG(entityID, "FFMPEG HLS", this, log, FFMPEGFormat.HLS,
                "-hide_banner -loglevel warning " + getFFMPEGInputOptions(), createHlsRtspUri(),
                buildHlsOptions(), getFfmpegHLSOutputPath().resolve("ipcamera.m3u8").toString(),
                videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(),
                () -> setAttribute(CHANNEL_START_STREAM, OnOffType.OFF));
            setAttribute("FFMPEG_HLS", new StringType(String.join(" ", ffmpegHLS.getCommandArrayList())));
        }
    }

    protected String createHlsRtspUri() {
        return getRtspUrlInternal(null);
    }

    protected void dispose0() {
        fireFfmpeg(ffmpegHLS, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegMP4, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegGIF, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegMjpeg, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegSnapshot, FFMPEG::stopConverting);
        ffMpegRtspAlarm.stop();
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

    private void dispose() {
        log.info("[{}]: Dispose video: <{}>", entityID, getEntity());
        isHandlerInitialized = false;
        disposeVideoConnectionJob();
        disposePollVideoJob();
        try {
            dispose0();
        } catch (Exception ex) {
            log.error("[{}]: Error while dispose video: <{}>", entityID, getEntity(), ex);
        }
    }

    private void disposeVideoConnectionJob() {
        if (videoConnectionJob != null) {
            videoConnectionJob.cancel();
            videoConnectionJob = null;
        }
    }

    private void disposePollVideoJob() {
        if (pollVideoJob != null) {
            pollVideoJob.cancel();
            pollVideoJob = null;
        }
    }

    @SneakyThrows
    private byte[] fireFfmpegSync(String profile, String output, String inputArguments, String outOptions, int maxTimeout) {
        Path path = Paths.get(output);
        try {
            Files.createFile(path);
            entityContext.media().fireFfmpeg(
                inputArguments + " " + getFFMPEGInputOptions(profile),
                snapshotSource,
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

    private String initSnapshotInput() {
        String rtspUri = getRtspUrlInternal(null);
        if (!entity.getPassword().isEmpty() && !rtspUri.contains("@") && rtspUri.contains("rtsp")) {
            String credentials = entity.getUser() + ":" + entity.getPassword().asString() + "@";
            return rtspUri.substring(0, 7) + credentials + rtspUri.substring(7);
        }
        return rtspUri;
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
        if (!snapshotPolling && !ffmpegSnapshotGeneration && lastUpdatedMs >= entity.getSnapshotPollInterval()) {
            updateSnapshot();
        }
        lockCurrentSnapshot.lock();
        try {
            return latestSnapshot;
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    protected void updateSnapshot() {

    }

    private class FFMpegRtspAlarm {

        private final Set<String> motionAlarmObservers = new HashSet<>();
        private FFMPEG ffmpegRtspHelper = null;
        private int motionThreshold;
        private int audioThreshold;

        public void addMotionAlarmListener(String listener) {
            motionAlarmObservers.add(listener);
            runFFMPEGRtspAlarmThread();
        }

        public void removeMotionAlarmListener(String listener) {
            motionAlarmObservers.remove(listener);
            if (motionAlarmObservers.isEmpty()) {
                stop();
            }
        }

        public void stop() {
            fireFfmpeg(ffmpegRtspHelper, FFMPEG::stopConverting);
        }

        private void runFFMPEGRtspAlarmThread() {
            T videoStreamEntity = BaseVideoService.this.getEntity();
            String inputOptions = BaseVideoService.this.getFFMPEGInputOptions();

            if (ffmpegRtspHelper != null) {
                // stop stream if threshold - 0
                if (videoStreamEntity.getAudioThreshold() == 0 && videoStreamEntity.getMotionThreshold() == 0) {
                    ffmpegRtspHelper.stopConverting();
                    return;
                }
                // if values that involved in precious run same as new - just skip restarting
                if (ffmpegRtspHelper.getIsAlive() && motionThreshold == videoStreamEntity.getMotionThreshold() &&
                    audioThreshold == videoStreamEntity.getAudioThreshold()) {
                    return;
                }
                ffmpegRtspHelper.stopConverting();
            }
            this.motionThreshold = videoStreamEntity.getMotionThreshold();
            this.audioThreshold = videoStreamEntity.getAudioThreshold();
            String input = defaultIfEmpty(entity.getAlarmInputUrl(), getRtspUri(null));

            List<String> filterOptionsList = new ArrayList<>();
            filterOptionsList.add(this.audioThreshold > 0 ? "-af silencedetect=n=-" + audioThreshold + "dB:d=2" : "-an");
            if (this.motionThreshold > 0) {
                filterOptionsList.addAll(videoStreamEntity.getMotionOptions());
                filterOptionsList.add("-vf select='gte(scene," + (motionThreshold / 100F) + ")',metadata=print");
            } else {
                filterOptionsList.add("-vn");
            }
            ffmpegRtspHelper = entityContext.media().buildFFMPEG(entityID, "FFMPEG rtsp alarm",
                BaseVideoService.this, log, FFMPEGFormat.RTSP_ALARMS, inputOptions, input,
                String.join(" ", filterOptionsList), "-f null -",
                entity.getUser(),
                entity.getPassword().asString(), null);
            fireFfmpeg(ffmpegRtspHelper, FFMPEG::startConverting);
            setAttribute("FFMPEG_RTSP_ALARM", new StringType(String.join(" ", ffmpegRtspHelper.getCommandArrayList())));
        }
    }
}

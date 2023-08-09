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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.Getter;
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
import org.homio.api.exception.ServerException;
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

    protected abstract BaseVideoStreamServerHandler createVideoStreamServerHandler();

    protected abstract void streamServerStarted();

    private static final Map<String, Integer> bootstrapServerPortMap = new HashMap<>();

    @Getter
    protected int serverPort;
    @Getter
    private Path ffmpegGifOutputPath;
    @Getter
    private Path ffmpegMP4OutputPath;
    @Getter
    private Path ffmpegHLSOutputPath;
    @Getter
    private Path ffmpegImageOutputPath;
    @Getter
    private final Map<String, State> attributes = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, State> requestAttributes = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Status>> stateListeners = new HashMap<>();
    private final FFMpegRtspAlarm ffMpegRtspAlarm = new FFMpegRtspAlarm();
    public ReentrantLock lockCurrentSnapshot = new ReentrantLock();
    public FFMPEG ffmpegHLS;
    @Getter
    private byte[] latestSnapshot = new byte[0];
    @Getter
    private long lastAnswerFromVideo;
    @Getter
    private boolean motionDetected = false;
    @Getter
    private FFMPEG ffmpegGIF;
    @Getter
    private FFMPEG ffmpegSnapshot;
    @Getter
    private FFMPEG ffmpegMjpeg;
    @Getter
    private FFMPEG ffmpegMP4 = null;
    @Getter
    private boolean isHandlerInitialized = false;
    private ThreadContext<Void> videoConnectionJob;
    private ThreadContext<Void> pollVideoJob;
    // actions holder
    private UIInputBuilder uiInputBuilder;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup serversLoopGroup = new NioEventLoopGroup();
    private String snapshotSource;
    private String snapshotInputOptions;
    private String mp4OutOptions;
    private String gifOutOptions;
    private long videoStreamParametersHashCode;

    public BaseVideoService(T entity, EntityContext entityContext) {
        super(entityContext);
        this.entity = entity;
    }

    public static int findFreeBootstrapServerPort() {
        AtomicInteger freePort = new AtomicInteger(9200);
        while (bootstrapServerPortMap.values().stream().anyMatch(h -> h == freePort.get())) {
            freePort.incrementAndGet();
        }
        return freePort.get();
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
                getRtspUri(profile),
                mp4OutOptions, filePath.toString(),
                entity.getUser(), entity.getPassword().asString(), null);
        fireFfmpeg(ffmpegMP4, FFMPEG::startConverting);
    }

    private boolean isRequireRestart() {
        return !isHandlerInitialized() || videoStreamParametersHashCode != entity.getVideoParametersHashCode();
    }

    public final void recordGif(Path filePath, @Nullable String profile, int secondsToRecord) {
        String gifInputOptions = "-y -t " + secondsToRecord + " -hide_banner -loglevel warning " + getFFMPEGInputOptions();
        ffmpegGIF = entityContext.media().buildFFMPEG(entityID, "FFMPEG GIF", this, log, FFMPEGFormat.GIF,
            gifInputOptions, getRtspUri(profile),
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

    public final void bringVideoOnline() {
        lastAnswerFromVideo = System.currentTimeMillis();
        if (pollVideoJob == null && isHandlerInitialized) {
            disposeVideoConnectionJob();
            pollVideoJob = entityContext.bgp().builder("poll-video-runnable-" + entityID)
                                        .intervalWithDelay(Duration.ofSeconds(8)).execute(this::pollVideoRunnable);
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
        }
    }

    public final void startStreamServer() {
        if (serverBootstrap == null) {
            try {
                serversLoopGroup = new NioEventLoopGroup();
                serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(serversLoopGroup);
                serverBootstrap.channel(NioServerSocketChannel.class);
                // IP "0.0.0.0" will bind the server to all network connections//
                serverBootstrap.localAddress(new InetSocketAddress("0.0.0.0", serverPort));
                serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addLast("idleStateHandler",
                            new IdleStateHandler(0, 60, 0));
                        socketChannel.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
                        socketChannel.pipeline().addLast("ChunkedWriteHandler", new ChunkedWriteHandler());
                        socketChannel.pipeline().addLast("streamServerHandler",
                            BaseVideoService.this.createVideoStreamServerHandler());
                    }
                });
                ChannelFuture serverFuture = serverBootstrap.bind().sync();
                serverFuture.await(4000);
                log.info("[{}]: File server for video at {} has started on port {} for all NIC's.", getEntityID(), getEntity(),
                    serverPort);
            } catch (Exception ex) {
                if (ex instanceof BindException) {
                    throw new ServerException("ERROR.CAMERA_PORT_BUSY", serverPort);
                }
                throw new IllegalStateException(
                    "Exception when starting server: " + CommonUtils.getErrorMessage(ex));
            }
            this.streamServerStarted();
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
        serverPort = entity.getServerPort();

        Path ffmpegOutputPath = CommonUtils.getMediaPath().resolve(entity.getFolderName()).resolve(entityID);
        ffmpegImageOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("images"));
        ffmpegGifOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("gif"));
        ffmpegMP4OutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("mp4"));
        ffmpegHLSOutputPath = CommonUtils.createDirectoriesIfNotExists(ffmpegOutputPath.resolve("hls"));
        try {
            FileUtils.cleanDirectory(ffmpegHLSOutputPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Unable to clean path: " + ffmpegHLSOutputPath);
        }

        bootstrapServerPortMap.remove(entityID);
        if (bootstrapServerPortMap.containsValue(serverPort)) {
            entity.setStatusError("Server port already in use");
            return;
        }
        bootstrapServerPortMap.put(entityID, serverPort);
        initialize();
    }

   /* @UIVideoActionGetter(CHANNEL_START_STREAM)
    public OnOffType getHKSStreamState() {
        return OnOffType.of(this.ffmpegHLSStarted);
    }*/

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

    protected final void fireFfmpeg(FFMPEG ffmpeg, Consumer<FFMPEG> handler) {
        if (ffmpeg != null) {
            handler.accept(ffmpeg);
        }
    }

    protected void pollingVideoConnection() {
        startSnapshot();
    }

    protected void pollVideoRunnable() {
        fireFfmpeg(ffmpegHLS, FFMPEG::stopProcessIfNoKeepAlive);

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

        String rtspUri = getRtspUri(null);

        ffmpegMjpeg = entityContext.media().buildFFMPEG(entityID, "FFMPEG mjpeg", this, log,
            FFMPEGFormat.MJPEG, getFFMPEGInputOptions() + " -hide_banner -loglevel warning", rtspUri,
            mgpegOutOptions, "http://127.0.0.1:" + serverPort + "/ipvideo.jpg",
            videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(), null);
        setAttribute("FFMPEG_MJPEG", new StringType(String.join(" ", ffmpegMjpeg.getCommandArrayList())));

        ffmpegSnapshot = entityContext.media().buildFFMPEG(entityID, "FFMPEG snapshot", this, log,
            FFMPEGFormat.SNAPSHOT, snapshotInputOptions, rtspUri,
            videoStreamEntity.getSnapshotOutOptionsAsString(),
            "http://127.0.0.1:" + serverPort + "/snapshot.jpg",
            videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(), () -> {
            });
        setAttribute("FFMPEG_SNAPSHOT", new StringType(String.join(" ", ffmpegSnapshot.getCommandArrayList())));

        if (videoStreamEntity instanceof AbilityToStreamHLSOverFFMPEG) {
            ffmpegHLS = entityContext.media().buildFFMPEG(entityID, "FFMPEG HLS", this, log, FFMPEGFormat.HLS,
                "-hide_banner -loglevel warning " + getFFMPEGInputOptions(), createHlsRtspUri(),
                buildHlsOptions(), getFfmpegHLSOutputPath().resolve("ipvideo.m3u8").toString(),
                videoStreamEntity.getUser(), videoStreamEntity.getPassword().asString(),
                () -> setAttribute(CHANNEL_START_STREAM, OnOffType.OFF));
            setAttribute("FFMPEG_HLS", new StringType(String.join(" ", ffmpegHLS.getCommandArrayList())));
        }

        startStreamServer();
    }

    protected String createHlsRtspUri() {
        return getRtspUri(null);
    }

    protected void dispose0() {
        fireFfmpeg(ffmpegHLS, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegMP4, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegGIF, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegMjpeg, FFMPEG::stopConverting);
        fireFfmpeg(ffmpegSnapshot, FFMPEG::stopConverting);
        ffMpegRtspAlarm.stop();
        stopStreamServer();
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
    private void stopStreamServer() {
        bootstrapServerPortMap.remove(entityID);
        serversLoopGroup.shutdownGracefully().sync();
        serverBootstrap = null;
    }

    @SneakyThrows
    private byte[] fireFfmpegSync(String profile, String output, String inputArguments, String outOptions, int maxTimeout) {
        try {
            Files.createFile(Paths.get(output));
            entityContext.media().fireFfmpeg(
                inputArguments + " " + getFFMPEGInputOptions(profile),
                snapshotSource,
                outOptions + " " + output,
                maxTimeout);
            Path path = Paths.get(output);
            return IOUtils.toByteArray(Files.newInputStream(path));
        } finally {
            try {
                Files.delete(Paths.get(output));
            } catch (IOException ex) {
                log.error("[{}]: Unable to remove file: <{}>", getEntityID(), output, ex);
            }
        }
    }

    private String initSnapshotInput() {
        String rtspUri = getRtspUri(null);
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

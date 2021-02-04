package org.touchhome.app.videoStream.handler;

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
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.touchhome.app.videoStream.entity.BaseFFmpegStreamEntity;
import org.touchhome.app.videoStream.ffmpeg.Ffmpeg;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.StringType;
import org.touchhome.bundle.api.model.Status;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;
import static org.touchhome.bundle.api.util.TouchHomeUtils.MACHINE_IP_ADDRESS;

@Log4j2
public abstract class BaseFfmpegCameraHandler<T extends BaseFFmpegStreamEntity> extends BaseCameraHandler<T> {

    public Ffmpeg ffmpegHLS = null;
    private Ffmpeg ffmpegRecord = null;
    private Ffmpeg ffmpegGIF = null;
    private Ffmpeg ffmpegRtspHelper = null;
    protected Ffmpeg ffmpegMjpeg = null;

    public String rtspUri = "";

    public int gifHistoryLength;
    public int mp4HistoryLength;

    private String gifFilename = "ipcamera";
    private String gifHistory = "";
    private int gifRecordTime = 5;

    private String mp4History = "";
    private String mp4Filename = "ipcamera";
    private int mp4RecordTime;

    protected int snapCount;

    private LinkedList<byte[]> fifoSnapshotBuffer = new LinkedList<>();

    private ServerBootstrap serverBootstrap;
    private EventLoopGroup serversLoopGroup = new NioEventLoopGroup();
    private ChannelFuture serverFuture = null;

    public BaseFfmpegCameraHandler(T cameraEntity, EntityContext entityContext) {
        super(cameraEntity, entityContext);
    }

    @Override
    protected void initialize0() {
        this.rtspUri = createRtspUri();
        startStreamServer();
    }

    protected abstract String createRtspUri();

    @Override
    protected void dispose0() {
        fireFfmpeg(ffmpegHLS, Ffmpeg::stopConverting);
        fireFfmpeg(ffmpegRecord, Ffmpeg::stopConverting);
        fireFfmpeg(ffmpegGIF, Ffmpeg::stopConverting);
        fireFfmpeg(ffmpegRtspHelper, Ffmpeg::stopConverting);
        fireFfmpeg(ffmpegMjpeg, Ffmpeg::stopConverting);

        ffmpegHLS = null;
        ffmpegRecord = null;
        ffmpegGIF = null;
        ffmpegRtspHelper = null;
        ffmpegMjpeg = null;

        stopStreamServer();
    }

    @Override
    public final void updateSnapshot() {
        startSnapshot();
    }

    protected void snapshotIsFfmpeg() {
        bringCameraOnline();
        updateImageChannel = false;
        ffmpegSnapshotGeneration = true;
        this.pollImage(true);
    }

    @Override
    public final void recordMp4(String fileName, int secondsToRecord) {
        mp4Filename = fileName;
        mp4RecordTime = secondsToRecord;
        startMP4Record();
        setAttribute(CHANNEL_RECORDING_MP4, new DecimalType(secondsToRecord));
    }

    @Override
    public final void recordGif(String fileName, int secondsToRecord) {
        gifFilename = fileName;
        gifRecordTime = secondsToRecord;
        if (cameraEntity.getGifPreroll() > 0) {
            snapCount = secondsToRecord;
        } else {
            startGiffRecord();
        }
        setAttribute(CHANNEL_RECORDING_GIF, new DecimalType(secondsToRecord));
    }

    public final void setMp4HistoryLength(int length) {
        if (length == 0) {
            mp4HistoryLength = 0;
            mp4History = "";
            setAttribute(CHANNEL_MP4_HISTORY, new StringType(mp4History));
        }
    }

    public final void setGifHistoryLength(int length) {
        if (length == 0) {
            gifHistoryLength = 0;
            gifHistory = "";
            setAttribute(CHANNEL_GIF_HISTORY, new StringType(gifHistory));
        }
    }

    public void noMotionDetected(String key) {
        setAttribute(key, OnOffType.OFF);
        motionDetected = false;
    }

    public void audioDetected() {
        attributes.put(CHANNEL_AUDIO_ALARM, OnOffType.ON);
    }

    public void noAudioDetected() {
        setAttribute(CHANNEL_AUDIO_ALARM, OnOffType.OFF);
    }

    public void processSnapshot(byte[] incomingSnapshot) {
        log.info("Got new snapshot for camera: <{}>", cameraEntity.getTitle());
        lockCurrentSnapshot.lock();
        try {
            currentSnapshot = incomingSnapshot;
            // fire ui that snapshot was updated
            entityContext.ui().updateItem(cameraEntity);

            if (cameraEntity.getGifPreroll() > 0) {
                fifoSnapshotBuffer.add(incomingSnapshot);
                if (fifoSnapshotBuffer.size() > (cameraEntity.getGifPreroll() + gifRecordTime)) {
                    fifoSnapshotBuffer.removeFirst();
                }
            }
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    @Override
    public void startSnapshot() {
        // if mjpeg stream you can use 'ffmpeg -i input -codec:v copy -bsf:v mjpeg2jpeg output.jpg'
        if (ffmpegSnapshot == null) {
            String inputOptions = getFFMPEGInputOptions() + " -threads 1 -skip_frame nokey -hide_banner -loglevel warning";
            ffmpegSnapshot = new Ffmpeg(this, FFmpegFormat.SNAPSHOT, ffmpegLocation, inputOptions, rtspUri,
                    cameraEntity.getSnapshotOptions(),
                    "http://127.0.0.1:" + serverPort + "/snapshot.jpg",
                    cameraEntity.getUser(), cameraEntity.getPassword());
        }
        fireFfmpeg(ffmpegSnapshot, Ffmpeg::startConverting);
    }

    public void startMJPEGRecord() {
        String inputOptions = getFFMPEGInputOptions();
        if (ffmpegMjpeg == null) {
            inputOptions += " -hide_banner -loglevel warning";
            ffmpegMjpeg = new Ffmpeg(this, FFmpegFormat.MJPEG, ffmpegLocation, inputOptions, rtspUri,
                    cameraEntity.getMjpegOptions(),
                    "http://127.0.0.1:" + serverPort + "/ipcamera.jpg",
                    cameraEntity.getUser(), cameraEntity.getPassword());
        }
        fireFfmpeg(ffmpegMjpeg, Ffmpeg::startConverting);
    }

    public void startRtspAlarms() {
        String inputOptions = getFFMPEGInputOptions();

        Ffmpeg localAlarms = ffmpegRtspHelper;
        if (localAlarms != null) {
            localAlarms.stopConverting();
            if (!audioAlarmEnabled && !motionAlarmEnabled) {
                return;
            }
        }
        String input = (cameraEntity.getAlarmInputUrl().isEmpty()) ? rtspUri : cameraEntity.getAlarmInputUrl();
        String filterOptions;
        if (!audioAlarmEnabled) {
            filterOptions = "-an";
        } else {
            filterOptions = "-af silencedetect=n=-" + audioThreshold + "dB:d=2";
        }
        if (!motionAlarmEnabled && !ffmpegSnapshotGeneration) {
            filterOptions = filterOptions.concat(" -vn");
        } else if (motionAlarmEnabled && !cameraEntity.getMotionOptions().isEmpty()) {
            String usersMotionOptions = cameraEntity.getMotionOptions();
            if (usersMotionOptions.startsWith("-")) {
                // Need to put the users custom options first in the chain before the motion is detected
                filterOptions += " " + usersMotionOptions + ",select='gte(scene," + motionThreshold
                        + ")',metadata=print";
            } else {
                filterOptions = filterOptions + " " + usersMotionOptions + " -vf select='gte(scene,"
                        + motionThreshold + ")',metadata=print";
            }
        } else if (motionAlarmEnabled) {
            filterOptions = filterOptions
                    .concat(" -vf select='gte(scene," + motionThreshold + ")',metadata=print");
        }
        ffmpegRtspHelper = new Ffmpeg(this, FFmpegFormat.RTSP_ALARMS, ffmpegLocation, inputOptions, input,
                filterOptions, "-f null -", cameraEntity.getUser(), cameraEntity.getPassword());
        fireFfmpeg(ffmpegRtspHelper, Ffmpeg::startConverting);
    }

    public void startMP4Record() {
        String inputOptions = getFFMPEGInputOptions();
        inputOptions = "-y -t " + mp4RecordTime + " -hide_banner -loglevel warning " + inputOptions;
        ffmpegRecord = new Ffmpeg(this, FFmpegFormat.RECORD, ffmpegLocation, inputOptions, rtspUri,
                cameraEntity.getMp4OutOptions(), ffmpegOutputPath.resolve(mp4Filename + ".mp4").toString(),
                cameraEntity.getUser(), cameraEntity.getPassword());
        fireFfmpeg(ffmpegRecord, ffmpeg -> {
            ffmpeg.startConverting();
            if (mp4History.isEmpty()) {
                mp4History = mp4Filename;
            } else if (!mp4Filename.equals("ipcamera")) {
                mp4History = mp4Filename + "," + mp4History;
                if (mp4HistoryLength > 49) {
                    int endIndex = mp4History.lastIndexOf(",");
                    mp4History = mp4History.substring(0, endIndex);
                }
            }
        });
        setAttribute(CHANNEL_MP4_HISTORY, new StringType(mp4History));
    }

    public void startGiffRecord() {
        String inputOptions = getFFMPEGInputOptions();
        if (cameraEntity.getGifPreroll() > 0) {
            ffmpegGIF = new Ffmpeg(this, FFmpegFormat.GIF, ffmpegLocation,
                    "-y -r 1 -hide_banner -loglevel warning", ffmpegOutputPath.resolve("snapshot%d.jpg").toString(),
                    "-frames:v " + (cameraEntity.getGifPreroll() + gifRecordTime) + " "
                            + cameraEntity.getGifOutOptions(),
                    ffmpegOutputPath.resolve(gifFilename + ".gif").toString(), cameraEntity.getUser(),
                    cameraEntity.getPassword());
        } else {
            inputOptions = "-y -t " + gifRecordTime + " -hide_banner -loglevel warning " + inputOptions;
            ffmpegGIF = new Ffmpeg(this, FFmpegFormat.GIF, ffmpegLocation, inputOptions, rtspUri,
                    cameraEntity.getGifOutOptions(), ffmpegOutputPath.resolve(gifFilename + ".gif").toString(),
                    cameraEntity.getUser(), cameraEntity.getPassword());
        }
        if (cameraEntity.getGifPreroll() > 0) {
            storeSnapshots();
        }
        fireFfmpeg(ffmpegGIF, ffmpeg -> {
            ffmpeg.startConverting();
            if (gifHistory.isEmpty()) {
                gifHistory = gifFilename;
            } else if (!gifFilename.equals("ipcamera")) {
                gifHistory = gifFilename + "," + gifHistory;
                if (gifHistoryLength > 49) {
                    int endIndex = gifHistory.lastIndexOf(",");
                    gifHistory = gifHistory.substring(0, endIndex);
                }
            }
            setAttribute(CHANNEL_GIF_HISTORY, new StringType(gifHistory));
        });
    }

    public void startHLSStream() {
        String inputOptions = getFFMPEGInputOptions();
        if (ffmpegHLS == null) {
            if (!inputOptions.isEmpty()) {
                ffmpegHLS = new Ffmpeg(this, FFmpegFormat.HLS, ffmpegLocation,
                        "-hide_banner -loglevel warning " + inputOptions, rtspUri,
                        cameraEntity.getHlsOutOptions(), ffmpegOutputPath.resolve("ipcamera.m3u8").toString(),
                        cameraEntity.getUser(), cameraEntity.getPassword());
            } else {
                ffmpegHLS = new Ffmpeg(this, FFmpegFormat.HLS, ffmpegLocation,
                        "-hide_banner -loglevel warning", rtspUri, cameraEntity.getHlsOutOptions(),
                        ffmpegOutputPath.resolve("ipcamera.m3u8").toString(), cameraEntity.getUser(),
                        cameraEntity.getPassword());
            }
        }
        fireFfmpeg(ffmpegHLS, Ffmpeg::startConverting);
    }

    private void storeSnapshots() {
        int count = 0;
        // Need to lock as fifoSnapshotBuffer is not thread safe and new snapshots can be incoming.
        lockCurrentSnapshot.lock();
        try {
            for (byte[] foo : fifoSnapshotBuffer) {
                Path file = ffmpegOutputPath.resolve("snapshot" + count + ".jpg");
                count++;
                try {
                    Files.write(file, foo);
                } catch (FileNotFoundException e) {
                    log.warn("FileNotFoundException {}", e.getMessage());
                } catch (IOException e) {
                    log.warn("IOException {}", e.getMessage());
                }
            }
        } finally {
            lockCurrentSnapshot.unlock();
        }
    }

    protected abstract String getFFMPEGInputOptions();

    public final Logger getLog() {
        return log;
    }

    public void motionDetected(String key) {
        attributes.put(CHANNEL_LAST_MOTION_TYPE, new StringType(key));
        attributes.put(key, OnOffType.ON);
        motionDetected = true;
    }

    @SneakyThrows
    private void stopStreamServer() {
        serversLoopGroup.shutdownGracefully().sync();
        serverBootstrap = null;
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
                        socketChannel.pipeline().addLast("streamServerHandler", BaseFfmpegCameraHandler.this.createCameraStreamServerHandler());
                    }
                });
                serverFuture = serverBootstrap.bind().sync();
                serverFuture.await(4000);
                log.info("File server for camera at {} has started on port {} for all NIC's.", cameraEntity, serverPort);
                attributes.put(CHANNEL_MJPEG_URL, new StringType("http://" + MACHINE_IP_ADDRESS + ":" + serverPort + "/ipcamera.mjpeg"));
                attributes.put(CHANNEL_HLS_URL, new StringType("http://" + MACHINE_IP_ADDRESS + ":" + serverPort + "/ipcamera.m3u8"));
                attributes.put(CHANNEL_IMAGE_URL, new StringType("http://" + MACHINE_IP_ADDRESS + ":" + serverPort + "/ipcamera.jpg"));
            } catch (Exception e) {
                cameraConfigError("Exception when starting server. Try changing the Server Port to another number.");
            }
            this.streamServerStarted();
        }
    }

    protected abstract BaseCameraStreamServerHandler createCameraStreamServerHandler();

    protected abstract void streamServerStarted();

    public final void cameraConfigError(String reason) {
        // won't try to reconnect again due to a config error being the cause.
        updateStatus(Status.OFFLINE, reason);
        dispose();
    }

    public void ffmpegError(String error) {
        this.updateStatus(Status.ERROR, error);
    }
}

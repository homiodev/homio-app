package org.homio.addon.camera;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.PRAGMA;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.onvif.impl.InstarBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.jetbrains.annotations.NotNull;
import org.onvif.ver10.schema.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/media/video")
@RequiredArgsConstructor
public class CameraController {

    private final EntityContext entityContext;
    public static final Map<String, OpenStreamsContainer> camerasOpenStreams = new ConcurrentHashMap<>();

    /**
     * Consume mjpeg from ffmpeg. See ffmpegMjpeg
     */
    @SneakyThrows
    @PostMapping("/{entityID}/ipcamera.jpg")
    public void postIpCamera(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        // ffmpeg sends data here for ipcamera.mjpeg streams when camera has no native stream.
        ServletInputStream snapshotData = req.getInputStream();
        getOpenStreamsContainer(entityID).openStreams.queueFrame(snapshotData.readAllBytes());
        snapshotData.close();
    }

    /**
     * Consume camera image snapshot
     */
    @SneakyThrows
    @PostMapping("/{entityID}/snapshot.jpg")
    public void postSnapshot(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        ServletInputStream snapshotData = req.getInputStream();
        BaseVideoEntity<?, ?> entity = entityContext.getEntityRequire(entityID);
        entity.getService().processSnapshot(snapshotData.readAllBytes());
        snapshotData.close();
    }

    @SneakyThrows
    @PostMapping("/{entityID}/OnvifEvent")
    public void postOnvifEvent(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        entity.getService().getOnvifDeviceState().getEventDevices().fireEvent(req.getReader().toString());
    }

    @SneakyThrows
    @GetMapping("/{entityID}/ipcamera.m3u8")
    public String getIpCameraM3U8(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        List<String> resolutions = entity.getStreamResolutions();
        if (resolutions.isEmpty()) {
            BaseVideoService<?, ?> service = entity.getService();
            FFMPEG ffmpeg = service.getFfmpegHLS();
            if (ffmpeg == null) {
                throw NotFoundException.fileNotFound(Paths.get("hls.m3u8"));
            }
            resp.setContentType("application/x-mpegURL");
            Path m3u8 = service.getFfmpegHLSOutputPath().resolve("hls.m3u8");
            if (!ffmpeg.getIsAlive()) {
                ffmpeg.stopConverting();
                deleteFilesFromFolder(service.getFfmpegHLSOutputPath());
                ffmpeg.startConverting();

                int retry = 10;
                while (retry-- > 0 && !Files.exists(m3u8)) {
                    sleep(1000);
                }
                sendFile(resp, m3u8);
            } else {
                ffmpeg.setKeepAlive(8);
                sendFile(resp, m3u8);
            }
        }
        StringBuilder file = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (String resolution : resolutions) {
            String[] wh = resolution.split("x");
            double bitRate = Integer.parseInt(wh[0]) * Integer.parseInt(wh[1]) / 20D * 30;
            int bandwidth = (int) (bitRate / 8);
            file.append("#EXT-X-STREAM-INF:BANDWIDTH=%s,RESOLUTION=%s\nrest/media/video/%s/hls.m3u8?bandwidth=%s\n\n"
                .formatted(bandwidth, resolution, entityID, resolution));
        }
        return file.toString();
    }

    @SneakyThrows
    private @NotNull BaseVideoEntity<?, ?> getEntity(String entityID) {
        BaseVideoEntity<?, ?> entity = entityContext.getEntityRequire(entityID);
        if (!entity.getStatus().isOnline()) {
            throw new ServerException("Unable to run execute request. Video entity: %s has wrong status: %s".formatted(entity.getTitle(), entity.getStatus()))
                .setStatus(HttpStatus.PRECONDITION_FAILED);
        }
        return entity;
    }

    private static void deleteFilesFromFolder(Path folder) {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folder)) {
            for (Path path : directoryStream) {
                if (Files.isRegularFile(path)) {
                    Files.delete(path);
                }
            }
        } catch (IOException ignore) {
        }
    }

    /* @GetMapping("/{entityID}/ipcamera.mpd")
    public void requestCameraIpCameraMpd(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseVideoEntity entity = getEntity(entityID);
        resp.setContentType("application/dash+xml");
        sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + "/ipcamera.mpd");
    }*/

    @GetMapping("/{entityID}/ipcamera.gif")
    public void requestCameraIpCameraGif(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        sendFile(entityID, resp, "image/gif", s -> s.getFfmpegGifOutputPath().resolve("ipcamera.gif"));
    }

    @GetMapping("/{entityID}/ipcamera.jpg")
    public void requestCameraIpCameraJpg(
            @PathVariable("entityID") String entityID,
            HttpServletRequest req,
            HttpServletResponse resp) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        BaseVideoService<?, ?> handler = entity.getService();
        // Use cached image if recent. Cameras can take > 1sec to send back a reply.
        // Example an Image item/widget may have a 1 second refresh.
        if (isUseCachedImage(handler)) {
            sendSnapshotImage(resp, handler.getSnapshot());
        } else {
            handler.getSnapshot();
            AsyncContext asyncContext = req.startAsync(req, resp);
            asyncContext.start(() -> {
                Instant startTime = Instant.now();
                do {
                    sleep(100);
                } // 5 sec timeout OR a new snapshot comes back from camera
                while (Duration.between(startTime, Instant.now()).toMillis() < 5000
                        && Duration.between(handler.getCurrentSnapshotTime(), Instant.now()).toMillis() > 1200);
                sendSnapshotImage(resp, handler.getSnapshot());
                asyncContext.complete();
            });
        }
    }

    private static boolean isUseCachedImage(BaseVideoService<?, ?> handler) {
        return Objects.requireNonNull(handler.getFfmpegSnapshot()).isRunning()
                || Duration.between(handler.getCurrentSnapshotTime(), Instant.now()).toMillis() < 1200;
    }

    @SneakyThrows
    @GetMapping("/{entityID}/snapshots.mjpeg")
    public void requestCameraSnapshotsMjpeg(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        BaseVideoService<?, ?> handler = entity.getService();
        handler.setStreamingSnapshotMjpeg(true);
        handler.startSnapshotPolling();
        StreamOutput output = new StreamOutput(resp);
        OpenStreams openSnapshotStreams = getOpenStreamsContainer(entityID).openSnapshotStreams;
        openSnapshotStreams.addStream(output);
        do {
            try {
                output.sendSnapshotBasedFrame(handler.getSnapshot());
                sleep(entity.getSnapshotPollInterval());
            } catch (Exception e) {
                // Never stop streaming until IOException. Occurs when browser stops the stream.
                openSnapshotStreams.removeStream(output);
                log.debug("Now there are {} snapshots.mjpeg streams open.",
                        openSnapshotStreams.getNumberOfStreams());
                if (openSnapshotStreams.isEmpty()) {
                    handler.setStreamingSnapshotMjpeg(false);
                    handler.stopSnapshotPolling();
                    log.debug("All snapshots.mjpeg streams have stopped.");
                }
                return;
            }
        } while (true);
    }

    @SneakyThrows
    @GetMapping("/{entityID}/ipcamera.mjpeg")
    public void requestCameraIpCameraMjpeg(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        BaseVideoService<?, ?> handler = entity.getService();
        StreamOutput output;
        OpenStreams openStreams = getOpenStreamsContainer(entityID).openStreams;
        String mjpegUri = handler.urls.getMjpegUri();

        if (openStreams.isEmpty()) {
            log.debug("First stream requested, opening up stream from camera");
            handler.openCamerasStream();
            if (mjpegUri.isEmpty() || "ffmpeg".equals(mjpegUri)) {
                output = new StreamOutput(resp);
            } else {
                output = new StreamOutput(resp, handler.getMjpegContentType());
            }
        } else if (mjpegUri.isEmpty() || "ffmpeg".equals(mjpegUri)) {
            output = new StreamOutput(resp);
        } else {
            ChannelTracking tracker = handler.getChannelTrack(handler.getTinyUrl(mjpegUri));
            if (tracker == null || !tracker.getChannel().isOpen()) {
                log.debug("Not the first stream requested but the stream from camera was closed");
                handler.openCamerasStream();
            }
            output = new StreamOutput(resp, handler.getMjpegContentType());
        }
        openStreams.addStream(output);
        do {
            try {
                output.sendFrame();
            } catch (InterruptedException | IOException e) {
                // Never stop streaming until IOException. Occurs when browser stops the stream.
                openStreams.removeStream(output);
                log.debug("Now there are {} ipcamera.mjpeg streams open.", openStreams.getNumberOfStreams());
                if (openStreams.isEmpty()) {
                    if (output.isSnapshotBased()) {
                        FFMPEG.run(handler.getFfmpegMjpeg(), FFMPEG::stopConverting);
                    } else {
                        handler.closeChannel(handler.getTinyUrl(mjpegUri));
                    }
                    log.debug("All ipcamera.mjpeg streams have stopped.");
                }
                return;
            }
        } while (!openStreams.isEmpty());
    }

    @SneakyThrows
    @GetMapping("/{entityID}/autofps.mjpeg")
    public void requestCameraAutoFps(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        BaseVideoService<?, ?> service = entity.getService();

        service.setStreamingAutoFps(true);
        StreamOutput output = new StreamOutput(resp);
        OpenStreams openAutoFpsStreams = getOpenStreamsContainer(entityID).openAutoFpsStreams;
        openAutoFpsStreams.addStream(output);
        int counter = 0;
        do {
            try {
                if (service.isMotionDetected()) {
                    output.sendSnapshotBasedFrame(service.getSnapshot());
                } // every 8 seconds if no motion or the first three snapshots to fill any FIFO
                else if (counter % 8 == 0 || counter < 3) {
                    output.sendSnapshotBasedFrame(service.getSnapshot());
                }
                counter++;
                sleep(1000);
            } catch (Exception e) {
                // Never stop streaming until IOException. Occurs when browser stops the stream.
                openAutoFpsStreams.removeStream(output);
                log.debug("Now there are {} autofps.mjpeg streams open.",
                        openAutoFpsStreams.getNumberOfStreams());
                if (openAutoFpsStreams.isEmpty()) {
                    service.setStreamingAutoFps(false);
                    log.debug("All autofps.mjpeg streams have stopped.");
                }
                return;
            }
        } while (true);
    }

    @GetMapping("/{entityID}/instar")
    public void requestCameraInstar(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        OnvifCameraEntity entity = (OnvifCameraEntity) getEntity(entityID);
        InstarBrandHandler instar = (InstarBrandHandler) entity.getService().getBrandHandler();
        instar.alarmTriggered(req.getPathInfo() + "?" + req.getQueryString());
    }

    @GetMapping("/{entityID}/{filename}.ts")
    public void requestCameraTs(
            @PathVariable("entityID") String entityID,
            @PathVariable("filename") String filename,
            HttpServletResponse resp) {
        sendFile(entityID, resp, "video/MP2T", s -> s.getFfmpegHLSOutputPath().resolve(filename + ".ts"));
    }

    @GetMapping("/{entityID}/{filename}.gif")
    public void requestCameraGif(
            @PathVariable("entityID") String entityID,
            @PathVariable("filename") String filename,
            HttpServletResponse resp) {
        sendFile(entityID, resp, "image/gif", s -> s.getFfmpegGifOutputPath().resolve(filename + ".gif"));
    }

    @GetMapping("/{entityID}/{filename}.jpg")
    public void requestCameraJpg(
            @PathVariable("entityID") String entityID,
            @PathVariable("filename") String filename,
            HttpServletResponse resp) {
        sendFile(entityID, resp, "image/jpg", s -> s.getFfmpegGifOutputPath().resolve(filename + ".jpg"));
    }

    @GetMapping("/{entityID}/{filename}.mp4")
    public void requestCameraMP4(
            @PathVariable("entityID") String entityID,
            @PathVariable("filename") String filename,
            HttpServletResponse resp) {
        sendFile(entityID, resp, "video/mp4", s -> s.getFfmpegGifOutputPath().resolve(filename + ".mp4"));
    }

    @GetMapping("/ffmpegWithProfiles")
    public List<OptionModel> getAllFFmpegWithProfiles() {
        List<OptionModel> list = new ArrayList<>();
        for (BaseVideoEntity<?, ?> videoStreamEntity : entityContext.findAll(BaseVideoEntity.class)) {
            if (videoStreamEntity.getStatus() == Status.ONLINE && videoStreamEntity.isStart()) {
                if (videoStreamEntity instanceof OnvifCameraEntity) {
                    OnvifCameraService service = (OnvifCameraService) videoStreamEntity.getService();
                    for (Profile profile : service.getOnvifDeviceState().getProfiles()) {
                        list.add(OptionModel.of(videoStreamEntity.getEntityID() + "/" + profile.getToken(),
                                videoStreamEntity.getTitle() + " (" + profile.getVideoEncoderConfiguration().getResolution().toString() + ")"));
                    }
                } else {
                    list.add(OptionModel.of(videoStreamEntity.getEntityID(), videoStreamEntity.getTitle()));
                }
            }
        }
        return list;
    }

    @SneakyThrows
    protected void sendSnapshotImage(HttpServletResponse response, byte[] snapshot) {
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        response.setContentType("image/jpg");
        if (snapshot.length == 1) {
            log.warn("ipcamera.jpg was requested but there was no jpg in ram to send.");
            return;
        }
        response.setContentLength(snapshot.length);
        ServletOutputStream servletOut = response.getOutputStream();
        servletOut.write(snapshot);
    }

    private static OpenStreamsContainer getOpenStreamsContainer(String entityID) {
        return camerasOpenStreams.computeIfAbsent(entityID, s -> new OpenStreamsContainer());
    }

    public static final class OpenStreamsContainer {

        public final OpenStreams openStreams = new OpenStreams();
        public final OpenStreams openSnapshotStreams = new OpenStreams();
        public final OpenStreams openAutoFpsStreams = new OpenStreams();

        public void dispose() {
            openStreams.closeAllStreams();
            openSnapshotStreams.closeAllStreams();
            openAutoFpsStreams.closeAllStreams();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignore) {}
    }

    private void sendFile(String entityID, HttpServletResponse resp, String contentType, Function<BaseVideoService<?, ?>, Path> pathSupplier) {
        BaseVideoEntity<?, ?> entity = getEntity(entityID);
        resp.setContentType(contentType);
        sendFile(resp, pathSupplier.apply(entity.getService()));
    }

    @SneakyThrows
    private void sendFile(HttpServletResponse response, Path file) {
        if (!Files.exists(file)) {
            throw NotFoundException.fileNotFound(file);
        }
        long fileSize = Files.size(file);
        if (fileSize <= 0) {
            throw new NotFoundException("File: %s found but empty".formatted(file));
        }
        response.setBufferSize((int) fileSize);
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        response.setHeader(CONTENT_LENGTH, String.valueOf(fileSize));
        response.setHeader(PRAGMA, "no-cache");
        response.setHeader(CACHE_CONTROL, "max-age=0, no-cache, no-store");
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file), (int) fileSize);
             BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream(), (int) fileSize)) {
            byte[] buffer = new byte[(int) fileSize];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
    }
}

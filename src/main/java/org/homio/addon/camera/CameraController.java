package org.homio.addon.camera;

import static org.homio.addon.camera.service.BaseCameraService.SHARE_DIR;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.entity.StreamHLS;
import org.homio.addon.camera.entity.StreamHLS.Resolution;
import org.homio.addon.camera.onvif.impl.InstarBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.service.BaseCameraService;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.onvif.ver10.schema.PTZPreset;
import org.onvif.ver10.schema.Profile;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/media/video")
@RequiredArgsConstructor
public class CameraController {

    private final EntityContext entityContext;
    public static final Map<String, OpenStreamsContainer> camerasOpenStreams = new ConcurrentHashMap<>();

    @GetMapping("/devices/pan")
    public List<OptionModel> getPanDevices() {
        return filterCameraDevices(ipCameraService -> ipCameraService.getOnvifDeviceState().getPtzDevices().isMoveSupported());
    }

    @GetMapping("/devices/zoom")
    public List<OptionModel> getZoomDevices() {
        return filterCameraDevices(ipCameraService -> ipCameraService.getOnvifDeviceState().getPtzDevices().isZoomSupported());
    }

    @GetMapping("/devices/presets")
    public List<OptionModel> getPresetsDevices() {
        return filterCameraDevices(ipCameraService -> {
            List<PTZPreset> presets = ipCameraService.getOnvifDeviceState().getPtzDevices().getPresets();
            return presets != null && !presets.isEmpty();
        });
    }

    @GetMapping("/presets")
    public @NotNull Collection<OptionModel> getCameraPresets(
        @RequestParam(value = "onvifCameraMenu") @NotNull String cameraEntityID) {
        IpCameraEntity entity = entityContext.getEntityRequire(cameraEntityID);
        return entity.getService().getPtzPresets();
    }

    /**
     * Consume mjpeg/image from ffmpeg. See ffmpegMjpeg/ffmpegSnapshot
     */
    @SneakyThrows
    @PostMapping("/{entityID}/snapshot.jpg")
    public void postIpCamera(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        // ffmpeg sends data here for ipcamera.mjpeg streams when camera has no native stream.
        ServletInputStream snapshotData = req.getInputStream();
        OpenStreamsContainer container = getOpenStreamsContainer(entityID);
        byte[] image = snapshotData.readAllBytes();
        container.openStreams.queueFrame(image);
        container.getEntity().getService().processSnapshot(image);
        snapshotData.close();
    }

    @SneakyThrows
    @PostMapping("/{entityID}/OnvifEvent")
    public void postOnvifEvent(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        IpCameraEntity entity = entityContext.getEntityRequire(entityID);
        entity.getService().getOnvifDeviceState().getEventDevices().fireEvent(req.getReader().toString());
    }

    @PostMapping("/{entityID}/mediamtx/video.webrtc")
    public ResponseEntity<?> postMediaMtxWebRTC(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, 8889, entityID, "whep", request.getQueryString(), ProxyExchange::post);
    }

    @PatchMapping("/{entityID}/mediamtx/video.webrtc")
    public ResponseEntity<?> patchMediaMtxWebRTC(@PathVariable("entityID") String ignore) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{entityID}/mediamtx/{filename}.m3u8")
    public ResponseEntity<?> getMediaMtxHls(@PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, 8888, entityID, filename + ".m3u8", request.getQueryString(), ProxyExchange::get);
    }

    @GetMapping("/{entityID}/mediamtx/{filename}.mp4")
    public ResponseEntity<?> getMediaMtxHlsMp4(@PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, 8888, entityID, filename + ".mp4", request.getQueryString(), ProxyExchange::get);
    }

    /**
     * Handle hls .m3u8 files
     */
    @GetMapping(value = "/{entityID}/video_low.m3u8", produces = "application/x-mpegURL")
    public ResponseEntity<String> getHLSLowResolution(@PathVariable("entityID") String entityID) {
        return getHLSFile(entityID, Resolution.low);
    }

    /**
     * Handle hls .m3u8 files
     */
    @GetMapping(value = "/{entityID}/video_high.m3u8", produces = "application/x-mpegURL")
    public ResponseEntity<String> getHLSHighResolution(@PathVariable("entityID") String entityID) {
        return getHLSFile(entityID, Resolution.high);
    }

    /**
     * Handle hls .m3u8 files
     */
    @GetMapping(value = "/{entityID}/video.m3u8", produces = "application/x-mpegURL")
    public ResponseEntity<String> getHLSDefaultResolution(@PathVariable("entityID") String entityID) {
        return getHLSFile(entityID, Resolution.def);
    }

    /**
     * Handle hls segment files
     */
    @GetMapping(value = "/{entityID}/{filename}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> requestHlsTS(@PathVariable("entityID") String ignore,
        @PathVariable("filename") String filename) {
        return getFile(filename, "ts");
    }

    /**
     * Handle dash .mpd file
     */
    @GetMapping(value = "/{entityID}/video.mpd", produces = "application/dash+xml")
    public ResponseEntity<String> requestCameraIpCameraMpd(@PathVariable("entityID") String entityID) {
        return createFfmpegAndGetFile(entityID, BaseCameraService::getOrCreateFfmpegDash, 60);
    }

    /**
     * Handle dash segment .m4s files
     */
    @GetMapping(value = "/{entityID}/{filename}.m4s", produces = "video/mp4")
    public ResponseEntity<Resource> requestDashM4S(
        @PathVariable("entityID") String ignore, @PathVariable("filename") String filename) {
        return getFile(filename, "m4s");
    }

    @GetMapping(value = "/{entityID}/ipcamera.gif", produces = "image/gif")
    public ResponseEntity<?> requestCameraIpCameraGif(@PathVariable("entityID") String entityID) {
        return getFile(entityID, s -> s.getFfmpegGifOutputPath().resolve("ipcamera.gif"), null);
    }

    @GetMapping("/{entityID}/snapshot.jpg")
    public void requestCameraIpCameraJpg(
        @PathVariable("entityID") String entityID,
        HttpServletRequest req,
        HttpServletResponse resp) {
        BaseCameraEntity<?, ?> entity = getEntity(entityID);
        BaseCameraService<?, ?> handler = entity.getService();
        // Use cached image if recent. Cameras can take > 1sec to send back a reply.
        // Example an Image item/widget may have a 1-second refresh.
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

    private static boolean isUseCachedImage(BaseCameraService<?, ?> handler) {
        return Objects.requireNonNull(handler.getFfmpegSnapshot()).isRunning()
                || Duration.between(handler.getCurrentSnapshotTime(), Instant.now()).toMillis() < 1200;
    }

    @SneakyThrows
    @GetMapping("/{entityID}/video.mjpeg")
    public void requestCameraIpCameraMjpeg(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        try {
            BaseCameraEntity<?, ?> entity = getEntity(entityID);
            BaseCameraService<?, ?> service = entity.getService();
            StreamOutput output;
            OpenStreams openStreams = getOpenStreamsContainer(entityID).openStreams;
            String mjpegUri = service.urls.getMjpegUri();

            if (openStreams.isEmpty()) {
                log.debug("First stream requested, opening up stream from camera");
                startMjpegStream(service);
                if (mjpegUri.isEmpty() || "ffmpeg".equals(mjpegUri)) {
                    output = new StreamOutput(resp);
                } else {
                    output = new StreamOutput(resp, service.getMjpegContentType());
                }
            } else if (mjpegUri.isEmpty() || "ffmpeg".equals(mjpegUri)) {
                output = new StreamOutput(resp);
            } else {
                ChannelTracking tracker = service.getChannelTrack(service.getTinyUrl(mjpegUri));
                if (tracker == null || !tracker.getChannel().isOpen()) {
                    log.info("Not the first stream requested but the stream from camera was closed");
                    startMjpegStream(service);
                }
                output = new StreamOutput(resp, service.getMjpegContentType());
            }
            output.setExtraStreamConsumer(service::processSnapshot);
            output.setSkipDuplicates(true);

            openStreams.addStream(output);
            do {
                try {
                    output.sendFrame();
                } catch (InterruptedException | IOException e) {
                    // Never stop streaming until IOException. Occurs when browser stops the stream.
                    openStreams.removeStream(output);
                    log.info("Now there are {} ipcamera.mjpeg streams open.", openStreams.getNumberOfStreams());
                    if (openStreams.isEmpty()) {
                        if (output.isSnapshotBased()) {
                            FFMPEG.run(service.getFfmpegMjpeg(), FFMPEG::stopConverting);
                        } else {
                            service.closeChannel(service.getTinyUrl(mjpegUri));
                        }
                        log.info("All ipcamera.mjpeg streams have stopped.");
                    }
                    return;
                }
            } while (!openStreams.isEmpty());
        } finally {
            log.info("[{}]: Closed all mjpeg", entityID);
        }
    }

    public static void startMjpegStream(BaseCameraService<?, ?> service) {
        service.startMjpegStream(() -> {
            OpenStreamsContainer container = camerasOpenStreams.get(service.getEntityID());
            if (container != null) {
                container.dispose();
            }
        });
    }

    @SneakyThrows
    @GetMapping("/{entityID}/autofps.mjpeg")
    public void requestCameraAutoFps(@PathVariable("entityID") String entityID, HttpServletResponse resp) {
        BaseCameraEntity<?, ?> entity = getEntity(entityID);
        BaseCameraService<?, ?> service = entity.getService();
        service.setStreamingAutoFps(true);
        StreamOutput output = new StreamOutput(resp);
        OpenStreams openAutoFpsStreams = getOpenStreamsContainer(entityID).openAutoFpsStreams;
        openAutoFpsStreams.addStream(output);

        try {
            int counter = 0;
            do {
                try {
                    if (service.isAlarmDetected()) {
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
                    return;
                }
            } while (true);
        } finally {
            if (openAutoFpsStreams.isEmpty()) {
                service.setStreamingAutoFps(false);
                log.debug("All autofps.mjpeg streams have stopped.");
            }
        }
    }

    @GetMapping("/{entityID}/instar")
    public void requestCameraInstar(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        IpCameraEntity entity = (IpCameraEntity) getEntity(entityID);
        InstarBrandHandler instar = (InstarBrandHandler) entity.getService().getBrandHandler();
        instar.alarmTriggered(req.getPathInfo() + "?" + req.getQueryString());
    }

    @GetMapping(value = "/{entityID}/{filename}.gif", produces = "image/gif")
    public ResponseEntity<?> requestCameraGif(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename) {
        return getFile(entityID, s -> s.getFfmpegGifOutputPath().resolve(filename + ".gif"), null);
    }

    @GetMapping(value = "/{entityID}/{filename}.jpg", produces = "image/jpg")
    public ResponseEntity<?> requestCameraJpg(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename) {
        return getFile(entityID, s -> s.getFfmpegGifOutputPath().resolve(filename + ".jpg"), null);
    }

    @GetMapping(value = "/{entityID}/{filename}.mp4", produces = "video/mp4")
    public @NotNull ResponseEntity<?> requestCameraMP4(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename,
        @RequestHeader HttpHeaders headers) {
        return getFile(entityID, s -> s.getFfmpegGifOutputPath().resolve(filename + ".mp4"), headers);
    }

    @GetMapping("/ffmpegWithProfiles")
    public List<OptionModel> getAllFFmpegWithProfiles() {
        List<OptionModel> list = new ArrayList<>();
        for (BaseCameraEntity<?, ?> videoStreamEntity : entityContext.findAll(BaseCameraEntity.class)) {
            if (videoStreamEntity.getStatus() == Status.ONLINE && videoStreamEntity.isStart()) {
                if (videoStreamEntity instanceof IpCameraEntity) {
                    IpCameraService service = (IpCameraService) videoStreamEntity.getService();
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

    private OpenStreamsContainer getOpenStreamsContainer(String entityID) {
        return camerasOpenStreams.computeIfAbsent(entityID, s ->
            new OpenStreamsContainer(entityContext.getEntity(entityID)));
    }

    @Getter
    @RequiredArgsConstructor
    public static final class OpenStreamsContainer {

        private final OpenStreams openStreams = new OpenStreams();
        private final OpenStreams openAutoFpsStreams = new OpenStreams();
        private final BaseCameraEntity<?, ?> entity;

        public void dispose() {
            openStreams.closeAllStreams();
            openAutoFpsStreams.closeAllStreams();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignore) {}
    }

    @SneakyThrows
    private ResponseEntity<?> getFile(
        @NotNull String entityID,
        @NotNull Function<BaseCameraService<?, ?>, Path> pathSupplier,
        @Nullable HttpHeaders headers) {
        BaseCameraEntity<?, ?> entity = getEntity(entityID);
        Path filePath = pathSupplier.apply(entity.getService());
        UrlResource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
        if (headers != null) {
            HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
            if (range != null) {
                long contentLength = resource.contentLength();
                long start = range.getRangeStart(contentLength);
                long end = range.getRangeEnd(contentLength);
                long rangeLength = Math.min(end - start + 1, contentLength);
                ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(region);
            }
        }
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    private ResponseEntity<String> getHLSFile(String entityID, @NotNull StreamHLS.Resolution resolution) {
        return createFfmpegAndGetFile(entityID, service -> service.getOrCreateFfmpegHls(resolution), 10);
    }

    @SneakyThrows
    private ResponseEntity<String> createFfmpegAndGetFile(String entityID,
        Function<BaseCameraService<?, ?>, FFMPEG> ffmpegCreator, int retrySec) {
        BaseCameraEntity<?, ?> entity = getEntity(entityID);
        BaseCameraService<?, ?> service = entity.getService();
        FFMPEG ffmpeg = ffmpegCreator.apply(service);
        Path outputFile = ffmpeg.getOutputFile();
        if (!ffmpeg.getIsAlive()) {
            ffmpeg.startConverting();

            while (retrySec-- > 0 && !Files.exists(outputFile)) {
                sleep(1000);
            }
        } else {
            ffmpeg.setKeepAlive(8);
        }
        try {
            return new ResponseEntity<>(Files.readString(outputFile), HttpStatus.OK);
        } catch (NoSuchFileException ne) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }


    @SneakyThrows
    private static @NotNull ResponseEntity<Resource> getFile(String filename, String extension) {
        UrlResource resource = new UrlResource(SHARE_DIR.resolve("%s.%s".formatted(filename, extension)).toUri());
        if (resource.exists()) {
            return new ResponseEntity<>(resource, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }

    @SneakyThrows
    private @NotNull BaseCameraEntity<?, ?> getEntity(String entityID) {
        BaseCameraEntity<?, ?> entity = entityContext.getEntityRequire(entityID);
        if (!entity.getStatus().isOnline()) {
            throw new ServerException("Unable to run execute request. Video entity: %s has wrong status: %s".formatted(entity.getTitle(), entity.getStatus()))
                .setStatus(HttpStatus.PRECONDITION_FAILED);
        }
        return entity;
    }

    private ResponseEntity<?> proxyUrl(ProxyExchange<byte[]> proxy, int port, String entityID, String path,
        String queryString, Function<ProxyExchange<byte[]>, ResponseEntity<byte[]>> handler) {
        if (queryString != null) {
            path += "?" + queryString;
        }
        ResponseEntity<byte[]> response = handler.apply(proxy.uri("http://localhost:%s/%s/%s".formatted(port, entityID, path)));
        HttpHeaders responseHeaders = new HttpHeaders();

        responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        return ResponseEntity.status(response.getStatusCode())
                             .headers(responseHeaders)
                             .body(response.getBody());
    }

    private @NotNull List<OptionModel> filterCameraDevices(Predicate<IpCameraService> filter) {
        return entityContext.toOptionModels(entityContext
            .findAll(IpCameraEntity.class)
            .stream()
            .filter(ipCameraEntity -> filter.test(ipCameraEntity.getService())).toList());
    }
}

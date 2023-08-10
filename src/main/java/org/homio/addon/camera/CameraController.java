package org.homio.addon.camera;

import static org.homio.addon.camera.service.BaseVideoService.fireFfmpeg;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.onvif.impl.InstarBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.onvif.ver10.schema.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/camera")
@RequiredArgsConstructor
public class CameraController {

    private static final long HLS_STARTUP_DELAY_MS = 4500;

    private final EntityContext entityContext;
    public static final OpenStreams openStreams = new OpenStreams();
    public static final OpenStreams openSnapshotStreams = new OpenStreams();
    public static final OpenStreams openAutoFpsStreams = new OpenStreams();

    @SneakyThrows
    @PostMapping("/{entityID}/ipcamera.jpg")
    public void postIpCamera(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        // ffmpeg sends data here for ipcamera.mjpeg streams when camera has no native stream.
        ServletInputStream snapshotData = req.getInputStream();
        openStreams.queueFrame(snapshotData.readAllBytes());
        snapshotData.close();
    }

    @SneakyThrows
    @PostMapping("/{entityID}/snapshot.jpg")
    public void postSnapshot(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        ServletInputStream snapshotData = req.getInputStream();
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        entity.getService().processSnapshot(snapshotData.readAllBytes());
        snapshotData.close();
    }

    @SneakyThrows
    @PostMapping("/{entityID}/OnvifEvent")
    public void postOnvifEvent(@PathVariable("entityID") String entityID, HttpServletRequest req) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        entity.getService().getOnvifDeviceState().getEventDevices().fireEvent(req.getReader().toString());
    }

    @GetMapping("/{entityID}/ipcamera.m3u8")
    public void getIpCameraM3U8(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {

        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        OnvifCameraService service = entity.getService();
        FFMPEG ffmpeg = service.ffmpegHLS;
        resp.setContentType("application/x-mpegURL");
        if (!ffmpeg.getIsAlive()) {
            ffmpeg.startConverting();
        } else {
            ffmpeg.setKeepAlive(8);
            sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + "/ipcamera.m3u8");
            return;
        }
        // Allow files to be created, or you get old m3u8 from the last time this ran.
        try {
            Thread.sleep(HLS_STARTUP_DELAY_MS);
        } catch (InterruptedException e) {
            return;
        }
        sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + "/ipcamera.m3u8");
    }
    
    @GetMapping("/{entityID}/ipcamera.mpd")
    public void requestCameraIpCameraMpd(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        resp.setContentType("application/dash+xml");
        sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + "/ipcamera.mpd");
    }

    @GetMapping("/{entityID}/ipcamera.gif")
    public void requestCameraIpCameraGif(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        resp.setContentType("image/gif");
        sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + "/ipcamera.gif");
    }

    @GetMapping("/{entityID}/ipcamera.jpg")
    public void requestCameraIpCameraJpg(
        @PathVariable("entityID") String entityID,
        HttpServletRequest req,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        OnvifCameraService handler = entity.getService();
        // Use cached image if recent. Cameras can take > 1sec to send back a reply.
        // Example an Image item/widget may have a 1 second refresh.
        if (handler.ffmpegSnapshotGeneration
            || Duration.between(handler.currentSnapshotTime, Instant.now()).toMillis() < 1200) {
            sendSnapshotImage(resp, handler.getSnapshot());
        } else {
            handler.getSnapshot();
            AsyncContext asyncContext = req.startAsync(req, resp);
            asyncContext.start(new Runnable() {
                @Override
                public void run() {
                    Instant startTime = Instant.now();
                    do {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            return;
                        }
                    } // 5 sec timeout OR a new snapshot comes back from camera
                    while (Duration.between(startTime, Instant.now()).toMillis() < 5000
                        && Duration.between(handler.currentSnapshotTime, Instant.now()).toMillis() > 1200);
                    sendSnapshotImage(resp, handler.getSnapshot());
                    asyncContext.complete();
                }
            });
        }
    }

    @SneakyThrows
    @GetMapping("/{entityID}/snapshots.mjpeg")
    public void requestCameraSnapshotsMjpeg(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        OnvifCameraService handler = entity.getService();
        handler.streamingSnapshotMjpeg = true;
        handler.startSnapshotPolling();
        StreamOutput output = new StreamOutput(resp);
        openSnapshotStreams.addStream(output);
        do {
            try {
                output.sendSnapshotBasedFrame(handler.getSnapshot());
                Thread.sleep(entity.getPollTime());
            } catch (InterruptedException | IOException e) {
                // Never stop streaming until IOException. Occurs when browser stops the stream.
                openSnapshotStreams.removeStream(output);
                log.debug("Now there are {} snapshots.mjpeg streams open.",
                    openSnapshotStreams.getNumberOfStreams());
                if (openSnapshotStreams.isEmpty()) {
                    handler.streamingSnapshotMjpeg = false;
                    handler.stopSnapshotPolling();
                    log.debug("All snapshots.mjpeg streams have stopped.");
                }
                return;
            }
        } while (true);
    }

    @SneakyThrows
    @GetMapping("/{entityID}/ipcamera.mjpeg")
    public void requestCameraIpCameraMjpeg(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        OnvifCameraService handler = entity.getService();
        StreamOutput output;
        if (openStreams.isEmpty()) {
            log.debug("First stream requested, opening up stream from camera");
            handler.openCamerasStream();
            if (handler.mjpegUri.isEmpty() || "ffmpeg".equals(handler.mjpegUri)) {
                output = new StreamOutput(resp);
            } else {
                output = new StreamOutput(resp, handler.mjpegContentType);
            }
        } else if (handler.mjpegUri.isEmpty() || "ffmpeg".equals(handler.mjpegUri)) {
            output = new StreamOutput(resp);
        } else {
            ChannelTracking tracker = handler.channelTrackingMap.get(handler.getTinyUrl(handler.mjpegUri));
            if (tracker == null || !tracker.getChannel().isOpen()) {
                log.debug("Not the first stream requested but the stream from camera was closed");
                handler.openCamerasStream();
            }
            output = new StreamOutput(resp, handler.mjpegContentType);
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
                        fireFfmpeg(handler.ffmpegMjpeg, FFMPEG::stopConverting);
                        // Set reference to ffmpegMjpeg to null to prevent automatic reconnection
                        // in handler's pollCameraRunnable() check for frozen camera
                        handler.ffmpegMjpeg = null;
                    } else {
                        handler.closeChannel(handler.getTinyUrl(handler.mjpegUri));
                    }
                    log.debug("All ipcamera.mjpeg streams have stopped.");
                }
                return;
            }
        } while (!openStreams.isEmpty());
    }

    @SneakyThrows
    @GetMapping("/{entityID}/autofps.mjpeg")
    public void requestCameraAutoFps(
        @PathVariable("entityID") String entityID,
        HttpServletResponse resp) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        OnvifCameraService service = entity.getService();

        service.streamingAutoFps = true;
        StreamOutput output = new StreamOutput(resp);
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
                Thread.sleep(1000);
            } catch (InterruptedException | IOException e) {
                // Never stop streaming until IOException. Occurs when browser stops the stream.
                openAutoFpsStreams.removeStream(output);
                log.debug("Now there are {} autofps.mjpeg streams open.",
                    openAutoFpsStreams.getNumberOfStreams());
                if (openAutoFpsStreams.isEmpty()) {
                    service.streamingAutoFps = false;
                    log.debug("All autofps.mjpeg streams have stopped.");
                }
                return;
            }
        } while (true);
    }

    @GetMapping("/{entityID}/instar")
    public void requestCameraInstar(
        @PathVariable("entityID") String entityID,
        HttpServletRequest req) {
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        InstarBrandHandler instar = (InstarBrandHandler) entity.getService().getBrandHandler();
        instar.alarmTriggered(req.getPathInfo() + "?" + req.getQueryString());
    }

    @GetMapping("/{entityID}/{filename}.ts")
    public void requestCameraTs(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename,
        HttpServletResponse resp) {
        resp.setContentType("video/MP2T");
        sendFile(entityID, filename, resp);
    }

    @GetMapping("/{entityID}/{filename}.gif")
    public void requestCameraGif(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename,
        HttpServletResponse resp) {
        resp.setContentType("image/gif");
        sendFile(entityID, filename, resp);
    }

    @GetMapping("/{entityID}/{filename}.jpg")
    public void requestCameraJpg(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename,
        HttpServletResponse resp) {
        resp.setContentType("image/jpg");
        sendFile(entityID, filename, resp);
    }

    @GetMapping("/{entityID}/{filename}.mp4")
    public void requestCameraMP4(
        @PathVariable("entityID") String entityID,
        @PathVariable("filename") String filename,
        HttpServletResponse resp) {
        resp.setContentType("video/mp4");
        sendFile(entityID, filename, resp);
    }

    private void sendFile(String entityID, String filename, HttpServletResponse resp) {
        String truncated = filename.substring(filename.lastIndexOf("/"));
        OnvifCameraEntity entity = entityContext.getEntityRequire(entityID);
        sendFile(resp, entity.getService().getFfmpegMP4OutputPath() + truncated);
    }

    @SneakyThrows
    private void sendFile(HttpServletResponse response, String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setBufferSize((int) file.length());
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "max-age=0, no-cache, no-store");
        BufferedInputStream input = null;
        BufferedOutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(file), (int) file.length());
            output = new BufferedOutputStream(response.getOutputStream(), (int) file.length());
            byte[] buffer = new byte[(int) file.length()];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        }
    }

    @GetMapping("/ffmpegWithProfiles")
    public List<OptionModel> getAllFFmpegWithProfiles() {
        List<OptionModel> list = new ArrayList<>();
        for (BaseVideoEntity videoStreamEntity : entityContext.findAll(BaseVideoEntity.class)) {
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
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setContentType("image/jpg");
        if (snapshot.length == 1) {
            log.warn("ipcamera.jpg was requested but there was no jpg in ram to send.");
            return;
        }
        response.setContentLength(snapshot.length);
        ServletOutputStream servletOut = response.getOutputStream();
        servletOut.write(snapshot);
    }
}

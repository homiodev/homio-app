package org.homio.app.rest;

import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.CameraPlaybackStorage.DownloadFile;
import org.homio.api.AddonEntrypoint;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.model.OptionModel;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.api.stream.video.VideoPlayer;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader.DynamicOptionLoaderParameters;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.ImageService;
import org.homio.app.manager.ImageService.ImageResponse;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.Go2RTCEntity;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity.VideoSeriesDataSourceDynamicOptionLoader;
import org.homio.app.rest.FileSystemController.NodeRequest;
import org.homio.app.service.FileSystemService;
import org.homio.app.spring.ContextCreated;
import org.homio.app.utils.MediaUtils;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;
import static org.springframework.http.HttpHeaders.*;

@Log4j2
@RestController
@RequestMapping("/rest/media")
@RequiredArgsConstructor
public class MediaController implements ContextCreated {

    private final FileSystemService fileSystemService;
    private final ImageService imageService;
    private final ContextImpl context;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final FfmpegHardwareRepository ffmpegHardwareRepository;
    private final AddonService addonService;
    private final FileSystemController fileSystemController;
    private final RetryPolicy<Path> PLAYBACK_THUMBNAIL_RETRY_POLICY =
            RetryPolicy.<Path>builder()
                    .handle(Exception.class)
                    .withDelay(Duration.ofSeconds(3))
                    .withMaxRetries(3)
                    .build();
    private final RetryPolicy<DownloadFile> PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY =
            RetryPolicy.<DownloadFile>builder()
                    .handle(Exception.class)
                    .withDelay(Duration.ofSeconds(5))
                    .withMaxRetries(3)
                    .build();

    private final Map<String, StreamContext> streams = new ConcurrentHashMap<>();
    private int go2rtcWebRtcPort;
    private int mtxWebRtcPort;
    private int mtxHlsPort;

    @Override
    public void onContextCreated(ContextImpl context) throws Exception {
        go2rtcWebRtcPort = Go2RTCEntity.getEntity(context).getWebRtcPort();
        MediaMTXEntity mtx = MediaMTXEntity.getEntity(context);
        mtxWebRtcPort = mtx.getWebRtcPort();
        mtxHlsPort = mtx.getHlsPort();
        context.event().addEntityUpdateListener(Go2RTCEntity.class, "media", upd ->
                go2rtcWebRtcPort = upd.getWebRtcPort());
        context.event().addEntityUpdateListener(MediaMTXEntity.class, "media", upd -> {
            mtxWebRtcPort = upd.getWebRtcPort();
            mtxHlsPort = upd.getHlsPort();
        });

        context.bgp().builder("audio-stream-cleanup")
                .intervalWithDelay(Duration.ofSeconds(60))
                .execute(this::removeTimedOutStreams);
    }

    @SneakyThrows
    @GetMapping("/stream/{streamID}")
    public void transferStream(@PathVariable String streamID, @RequestHeader HttpHeaders headers,
                               @NotNull HttpServletResponse resp) {
        StreamContext streamContext = streams.get(streamID);
        if (streamContext == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Stream not found");
            return;
        }

        streamContext.lastRequested = System.currentTimeMillis();
        MimeType mimeType = streamContext.getMediaType();

        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
        Resource resource = streamContext.stream.getResource();

        try {
            if (isHasResourceRegion(range)) {
                ResponseEntity<ResourceRegion> response = resourceRegion(resource, resource.contentLength(), headers, mimeType);
                ResourceRegion resourceRegion = response.getBody();
                if (resourceRegion != null) {
                    ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(resp);
                    new MediaResourceRegionHttpMessageConverter().writeResourceRegionInternal(resourceRegion,
                            httpResponse, streamContext);
                }
            } else {
                resp.setHeader(CONTENT_TYPE, mimeType.toString());
                long contentLength = resource.contentLength();
                if (contentLength > 0) {
                    resp.setContentLength((int) contentLength);
                }
                streamFullContent(resource, streamContext, resp);
            }
        } catch (IOException e) {
            log.error("Error streaming the resource: {}", e.getMessage());
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void streamFullContent(Resource resource, StreamContext streamContext, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = resource.getInputStream(); ServletOutputStream outputStream = resp.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
                streamContext.lastRequested = System.currentTimeMillis();
            }
        }
    }

    private static boolean isHasResourceRegion(HttpRange range) {
        return range != null && !"0-".equals(range.toString());
    }

    @SneakyThrows
    @GetMapping("/video/{jsonContent}/play")
    public ResponseEntity<ResourceRegion> playFile(@PathVariable("jsonContent") String jsonContent, @RequestHeader HttpHeaders headers) {
        byte[] content = Base64.getDecoder().decode(jsonContent.getBytes());
        NodeRequest nodeRequest = OBJECT_MAPPER.readValue(content, NodeRequest.class);
        FileSystemProvider fileSystem = fileSystemService.getFileSystem(nodeRequest.sourceFs, nodeRequest.alias);
        Resource resource = fileSystem.getEntryResource(nodeRequest.getSourceFileId());
        String videoType = MediaUtils.getVideoType(fileSystem.toTreeNode(nodeRequest.getSourceFileId()).getName());
        MediaType type = MediaType.parseMediaType(videoType);
        return resourceRegion(resource, resource.contentLength(), headers, type);
    }

    @GetMapping("/video/{entityID}/sources")
    public List<OptionModel> getVideoSources(@PathVariable("entityID") String entityID) {
        BaseEntity baseEntity = getEntity(entityID);
        var parameters = new DynamicOptionLoaderParameters(baseEntity, context, new String[0], null);
        List<OptionModel> models = new VideoSeriesDataSourceDynamicOptionLoader().loadOptions(parameters);
        return models.isEmpty() ? models : models.get(0).getChildren();
    }

    @PostMapping("/{entityID}/go2rtc/video.webrtc")
    public ResponseEntity<?> postGo2RTCWebRTC(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, go2rtcWebRtcPort, entityID, "whep", request.getQueryString(), ProxyExchange::post);
    }

    @PostMapping("/{entityID}/mediamtx/video.webrtc")
    public ResponseEntity<?> postMediaMtxWebRTC(@PathVariable("entityID") String entityID, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, mtxWebRtcPort, entityID, "whep", request.getQueryString(), ProxyExchange::post);
    }

    @PatchMapping("/{entityID}/mediamtx/video.webrtc")
    public ResponseEntity<?> patchMediaMtxWebRTC(@PathVariable("entityID") String ignore) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{entityID}/mediamtx/{filename}.m3u8")
    public ResponseEntity<?> getMediaMtxHls(@PathVariable("entityID") String entityID,
                                            @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, mtxHlsPort, entityID, filename + ".m3u8", request.getQueryString(), ProxyExchange::get);
    }

    @GetMapping("/{entityID}/mediamtx/{filename}.mp4")
    public ResponseEntity<?> getMediaMtxHlsMp4(@PathVariable("entityID") String entityID,
                                               @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
        return proxyUrl(proxy, mtxHlsPort, entityID, filename + ".mp4", request.getQueryString(), ProxyExchange::get);
    }

    @GetMapping("/video/playback/days/{entityID}/{from}/{to}")
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(
            @PathVariable("entityID") String entityID,
            @PathVariable(value = "from") @DateTimeFormat(pattern = "yyyyMMdd") Date from,
            @PathVariable(value = "to") @DateTimeFormat(pattern = "yyyyMMdd") Date to)
            throws Exception {
        CameraPlaybackStorage entity = context.db().getRequire(entityID);
        return entity.getAvailableDaysPlaybacks(context, "main", from, to);
    }

    @GetMapping("/video/playback/files/{entityID}/{date}")
    public List<CameraPlaybackStorage.PlaybackFile> getPlaybackFiles(
            @PathVariable("entityID") String entityID,
            @PathVariable(value = "date") @DateTimeFormat(pattern = "yyyyMMdd") Date date)
            throws Exception {
        CameraPlaybackStorage entity = context.db().getRequire(entityID);
        return entity.getPlaybackFiles(context, "main", date, new Date(date.getTime() + TimeUnit.DAYS.toMillis(1) - 1));
    }

    @PostMapping("/video/playback/{entityID}/thumbnails/base64")
    public ResponseEntity<List<String>> getPlaybackThumbnailsBase64(
            @PathVariable("entityID") String entityID,
            @RequestBody ThumbnailRequest thumbnailRequest,
            @RequestParam(value = "size", defaultValue = "800x600") String size)
            throws Exception {
        Map<String, Path> filePathList = thumbnailRequest.fileIds
                .stream()
                .collect(Collectors.toMap(id -> id, id -> getPlaybackThumbnailPath(entityID, id, size)));

        Thread.sleep(500); // wait till ffmpeg close all file handlers
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : filePathList.entrySet()) {
            Path filePath = entry.getValue();
            if (filePath == null || !Files.exists(filePath)) {
                result.add(entry.getKey() + LIST_DELIMITER);
            } else {
                result.add(entry.getKey() + LIST_DELIMITER + "data:image/jpg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
            }
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/video/playback/{entityID}/{fileId}/thumbnail/jpg",
            produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getPlaybackThumbnailJpg(
            @PathVariable("entityID") String entityID,
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "size", defaultValue = "800x600") String size)
            throws Exception {
        Path path = getPlaybackThumbnailPath(entityID, fileId, size);
        return new ResponseEntity<>(path == null ? new byte[0] : Files.readAllBytes(path), HttpStatus.OK);
    }

    @GetMapping("/video/playback/{entityID}/{fileId}/download")
    public ResponseEntity<ResourceRegion> downloadPlaybackFile(
            @PathVariable("entityID") String entityID,
            @PathVariable("fileId") String fileId,
            @RequestHeader HttpHeaders headers)
            throws IOException {
        CameraPlaybackStorage entity = context.db().getRequire(entityID);
        String ext = StringUtils.defaultIfEmpty(FilenameUtils.getExtension(fileId), "mp4");
        Path path = CommonUtils.getMediaPath().resolve("camera").resolve(entityID).resolve("playback").resolve(fileId + "." + ext);

        DownloadFile downloadFile;

        if (Files.exists(path)) {
            downloadFile = new DownloadFile(new UrlResource(path.toUri()), Files.size(path), fileId, null);
        } else {
            downloadFile = Failsafe.with(PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY)
                    .onFailure(event ->
                            log.error("Unable to download playback file: <{}>. <{}>. Msg: <{}>", entity.getTitle(), fileId,
                                    getErrorMessage(event.getException())))
                    .get(executionContext -> {
                        log.info("Reply <{}>. Download playback video file <{}>. <{}>",
                                executionContext.getAttemptCount(), entity.getTitle(), fileId);
                        return entity.downloadPlaybackFile(context, "main", fileId, path);
                    });
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(downloadFile.stream())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return resourceRegion(downloadFile.stream(), downloadFile.size(), headers, mediaType);
    }

    @SneakyThrows
    @GetMapping("/image/{entityID}")
    public ResponseEntity<InputStreamResource> getImage(
            WebRequest webRequest,
            @PathVariable String entityID,
            @RequestParam(value = "fs", required = false) String fs) {
        String eTag = String.valueOf((entityID + StringUtils.trimToEmpty(fs)).hashCode());
        if (webRequest.checkNotModified(eTag)) {
            return null;
        }
        if (StringUtils.isNotEmpty(fs)) {
            return fileSystemController.download(fs, new NodeRequest(entityID));
        }
        return toResponse(imageService.getImage(entityID), eTag);
    }

    @GetMapping("/workspace/extension/{addonID}.png")
    public ResponseEntity<InputStreamResource> getExtensionImage(WebRequest webRequest, @PathVariable("addonID") String addonID) {
        String eTag = String.valueOf(addonID.hashCode());
        if (webRequest.checkNotModified(eTag)) {
            return null;
        }
        AddonEntrypoint addonEntrypoint = addonService.getAddon(addonID);
        InputStream stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("extensions/" + addonEntrypoint.getAddonID() + ".png");
        if (stream == null) {
            stream = addonEntrypoint.getClass().getClassLoader().getResourceAsStream("images/image.png");
        }
        if (stream == null) {
            throw new NotFoundException("Unable to find workspace extension addon image for addon: " + addonID);
        }
        return toResponse(new ImageResponse(stream, MediaType.IMAGE_PNG), eTag);
    }

    @SneakyThrows
    @GetMapping("/image/{addonID}/{imageID:.+}")
    public ResponseEntity<InputStreamResource> getAddonImage(
            WebRequest webRequest,
            @PathVariable("addonID") String addonID,
            @PathVariable String imageID) {
        String eTag = String.valueOf((addonID + imageID).hashCode());
        if (webRequest.checkNotModified(eTag)) {
            return null;
        }
        return toResponse(imageService.getAddonImage(addonID, imageID), eTag);
    }

    /* TODO: @GetMapping("/audio")
    public Collection<OptionModel> getAudioFiles() {
        return SettingPluginOptionsFileExplorer.getFilePath(CommonUtils.getAudioPath(), 7, false,
                false, true, null, null,
                null, (path, basicFileAttributes) -> {
                    // return new Tika().detect(path).startsWith("audio/");
                    String name = path.getFileName().toString();
                    return name.endsWith(".mp4") || name.endsWith(".wav") || name.endsWith(".mp3");
                }, null,
                path -> path.getFileName() == null ? path.toString() : path.getFileName().toString());
    }*/

    @GetMapping("/stream")
    public Collection<OptionModel> getStreamPlayers(@RequestParam(value = "filter", required = false) String filter) {
        Set<OptionModel> models = new HashSet<>();
        if (isEmpty(filter) || filter.equals("audio")) {
            for (AudioPlayer player : context.media().getAudioPlayers().values()) {
                models.add(OptionModel.of(player.getId(), player.getLabel()));
            }
        }
        if (isEmpty(filter) || filter.equals("video")) {
            for (VideoPlayer player : context.media().getVideoPlayers().values()) {
                models.add(OptionModel.of(player.getId(), player.getLabel()));
            }
        }
        return models;
    }

    @SneakyThrows
    private Path getPlaybackThumbnailPath(String entityID, String fileId, String size) {
        CameraPlaybackStorage entity = context.db().getRequire(entityID);
        Path path = CommonUtils.getMediaPath().resolve("camera").resolve(entityID).resolve("playback")
                .resolve(fileId + "_" + size.replaceAll(":", "x") + ".jpg");
        if (Files.exists(path) && Files.size(path) > 0) {
            return path;
        }
        CommonUtils.createDirectoriesIfNotExists(path.getParent());
        Files.deleteIfExists(path);

        URI uri = entity.getPlaybackVideoURL(context, fileId);
        String uriStr = uri.getScheme().equals("file") ? Paths.get(uri).toString() : uri.toString();

        Fallback<Path> fallback = Fallback.of((Path) null);
        return Failsafe.with(PLAYBACK_THUMBNAIL_RETRY_POLICY, fallback)
                .onFailure(event ->
                        log.error("Unable to get playback img: <{}>. Msg: <{}>", entity.getTitle(), getErrorMessage(event.getException())))
                .get(context -> {
                    fireFFmpeg(fileId, size, entity, path, uriStr, context);
                    return path;
                });
    }

    private void fireFFmpeg(String fileId, String size, CameraPlaybackStorage entity, Path path, String uriStr, ExecutionContext<Path> context) {
        log.info("Reply <{}>. playback img <{}>. <{}>", context.getAttemptCount(), entity.getTitle(), fileId);
        ffmpegHardwareRepository.fireFfmpeg(
                FFMPEG_LOCATION, "-y", "\"" + uriStr + "\"", format("-frames:v 1 -vf scale=%s -q:v 3 %s", size, path), // q:v - jpg quality
                60);
    }

    private ResponseEntity<ResourceRegion> resourceRegion(Resource resource, long contentLength, HttpHeaders headers, MimeType mimeType) {
        HttpStatus status = HttpStatus.PARTIAL_CONTENT;
        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
        ResourceRegion region;
        if (range == null) {
            region = new ResourceRegion(resource, 0, contentLength);
        } else {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1; // Math.min(1024 * 1024, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
        }

        return ResponseEntity
                .status(status)
                .header("Accept-Ranges", "bytes")
                .contentType((MediaType) mimeType)
                .contentLength(contentLength)
                .body(region);
    }

    private ResponseEntity<InputStreamResource> toResponse(ImageResponse response, String eTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("image", "jpeg"));
        headers.add(CACHE_CONTROL, "max-age=31536000, public");
        headers.add(ETAG, eTag);
        headers.add(LAST_MODIFIED, new Date(System.currentTimeMillis()).toString());
        return CommonUtils.inputStreamToResource(response.stream(), response.mediaType(), headers);
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

    @SneakyThrows
    private @NotNull BaseCameraEntity<?, ?> getEntity(String entityID) {
        BaseCameraEntity<?, ?> entity = context.db().getRequire(entityID);
        if (!entity.getStatus().isOnline()) {
            throw new ServerException("Unable to run execute request. Video entity: %s has wrong status: %s".formatted(entity.getTitle(), entity.getStatus()))
                    .setHttpStatus(HttpStatus.PRECONDITION_FAILED);
        }
        return entity;
    }

    public String createStreamUrl(ContentStream stream, int timeoutOnInactiveSeconds) {
        String streamId = CommonUtils.generateUUID();
        StreamContext context = new StreamContext(stream, timeoutOnInactiveSeconds);
        streams.put(streamId, context);
        return "rest/media/stream/" + streamId;
    }

    private synchronized void removeTimedOutStreams() {
        long now = System.currentTimeMillis();
        streams.entrySet().removeIf(entry -> {
            StreamContext context = entry.getValue();
            boolean remove = context.lastRequested + context.timeoutOnInactiveSeconds < now;
            if (remove) {
                log.warn("Removed timed out stream <{}>", entry.getKey());
                IOUtils.closeQuietly(context.stream);
            }
            return remove;
        });
    }

    @Getter
    @Setter
    public static class ThumbnailRequest {

        private List<String> fileIds;
    }

    private static class StreamContext {

        private final ContentStream stream;

        private final int timeoutOnInactiveSeconds;

        // timeout since last request to wait before dispose stream
        private long lastRequested;

        public StreamContext(ContentStream stream, int timeoutOnInactiveSeconds) {
            this.stream = stream;
            this.timeoutOnInactiveSeconds = timeoutOnInactiveSeconds;
            this.lastRequested = System.currentTimeMillis();
        }

        public @NotNull MimeType getMediaType() {
            try {
                return stream.getStreamFormat().getMimeType();
            } catch (Exception ignore) {
                return MediaType.APPLICATION_OCTET_STREAM;
            }
        }
    }

    private static class MediaResourceRegionHttpMessageConverter extends ResourceRegionHttpMessageConverter {

        @SneakyThrows
        public void writeResourceRegionInternal(ResourceRegion region, ServletServerHttpResponse outputMessage, StreamContext streamContext) {
            super.writeResourceRegion(region, outputMessage);
            streamContext.lastRequested = System.currentTimeMillis();
        }
    }
}

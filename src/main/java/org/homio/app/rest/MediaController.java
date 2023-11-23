package org.homio.app.rest;

import static java.lang.String.format;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.LAST_MODIFIED;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.CameraPlaybackStorage.DownloadFile;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.SelfContainedAudioSourceContainer;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader.DynamicOptionLoaderParameters;
import org.homio.api.util.CommonUtils;
import org.homio.app.audio.AudioService;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.ImageService;
import org.homio.app.manager.ImageService.ImageResponse;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.Go2RTCEntity;
import org.homio.app.model.entity.MediaMTXEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity.VideoSeriesDataSourceDynamicOptionLoader;
import org.homio.app.rest.FileSystemController.ListRequest;
import org.homio.app.spring.ContextCreated;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@Log4j2
@RestController
@RequestMapping("/rest/media")
@RequiredArgsConstructor
public class MediaController implements ContextCreated {

    private int go2rtcWebRtcPort;
    private int mtxWebRtcPort;
    private int mtxHlsPort;

    @Override
    public void onContextCreated(ContextImpl context) throws Exception {
        go2rtcWebRtcPort = Go2RTCEntity.ensureEntityExists(context).getWebRtcPort();
        MediaMTXEntity mtx = MediaMTXEntity.ensureEntityExists(context);
        mtxWebRtcPort = mtx.getWebRtcPort();
        mtxHlsPort = mtx.getHlsPort();
        context.event().addEntityUpdateListener(Go2RTCEntity.class, "media", upd -> {
            go2rtcWebRtcPort = upd.getWebRtcPort();
        });
        context.event().addEntityUpdateListener(MediaMTXEntity.class, "media", upd -> {
            mtxWebRtcPort = upd.getWebRtcPort();
            mtxHlsPort = upd.getHlsPort();
        });
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

    private static final Cache<String, MediaPlayContext> fileIdToMedia = CacheBuilder
        .newBuilder().expireAfterAccess(Duration.ofHours(24)).build();

    private final ImageService imageService;
    private final Context context;
    private final AudioService audioService;
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

    public static @NotNull String createVideoPlayLink(@NotNull FileSystemProvider fileSystem, @NotNull String resource, String videoType, String extension) {
        String id = "file_" + resource.hashCode();
        fileIdToMedia.put(id, new MediaPlayContext(fileSystem, resource, resource, fileSystem.size(resource), videoType));
        return "$DEVICE_URL/rest/media/video/" + id + "/play";
    }

    /*@GetMapping("/video/{fileId}/content")
    public ResponseEntity<Resource> getVideoFileContent(@PathVariable("fileId") String fileId) {
        MediaPlayContext context = fileIdToMedia.getIfPresent(fileId);
        if (context == null) {
            throw NotFoundException.fileNotFound(fileId);
        }
        Resource resource = context.fileSystem.getEntryResource(context.id);
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.parseMediaType(context.type)).body(resource);
    }*/

    @GetMapping("/video/{fileId}/play")
    public ResponseEntity<ResourceRegion> downloadFile(@PathVariable("fileId") String fileId, @RequestHeader HttpHeaders headers) {
        MediaPlayContext context = fileIdToMedia.getIfPresent(fileId);
        if (context == null) {
            throw NotFoundException.fileNotFound(fileId);
        }
        Resource resource = context.fileSystem.getEntryResource(context.id);
        ResourceRegion region = resourceRegion(resource, context.size, headers);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(MediaType.parseMediaType(context.type)).body(region);
    }

    @GetMapping("/video/playback/days/{entityID}/{from}/{to}")
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(
        @PathVariable("entityID") String entityID,
        @PathVariable(value = "from") @DateTimeFormat(pattern = "yyyyMMdd") Date from,
        @PathVariable(value = "to") @DateTimeFormat(pattern = "yyyyMMdd") Date to)
        throws Exception {
        CameraPlaybackStorage entity = context.db().getEntityRequire(entityID);
        return entity.getAvailableDaysPlaybacks(context, "main", from, to);
    }

    @GetMapping("/video/playback/files/{entityID}/{date}")
    public List<CameraPlaybackStorage.PlaybackFile> getPlaybackFiles(
        @PathVariable("entityID") String entityID,
        @PathVariable(value = "date") @DateTimeFormat(pattern = "yyyyMMdd") Date date)
        throws Exception {
        CameraPlaybackStorage entity = context.db().getEntityRequire(entityID);
        return entity.getPlaybackFiles(context, "main", date, new Date(date.getTime() + TimeUnit.DAYS.toMillis(1) - 1));
    }

    @PostMapping("/video/playback/{entityID}/thumbnails/base64")
    public ResponseEntity<List<String>> getPlaybackThumbnailsBase64(
        @PathVariable("entityID") String entityID,
        @RequestBody ThumbnailRequest thumbnailRequest,
        @RequestParam(value = "size", defaultValue = "800x600") String size)
        throws Exception {
        Map<String, Path> filePathList = thumbnailRequest.fileIds.stream().sequential()
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
        CameraPlaybackStorage entity = context.db().getEntityRequire(entityID);
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

        ResourceRegion region = resourceRegion(downloadFile.stream(), downloadFile.size(), headers);
        MediaType mediaType = MediaTypeFactory.getMediaType(downloadFile.stream()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(mediaType).body(region);
    }

    @SneakyThrows
    @GetMapping("/audio/{streamID}/play")
    public void playAudioFile(@PathVariable String streamID, HttpServletResponse resp) {
        audioService.playRequested(streamID, resp);
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
            return fileSystemController.download(fs, new ListRequest(entityID));
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

    @GetMapping("/audioSource")
    public Collection<OptionModel> audioSource() {
        Collection<OptionModel> optionModels = new ArrayList<>();
        for (SelfContainedAudioSourceContainer audioSourceContainer :
            audioService.getAudioSourceContainers()) {
            String label = audioSourceContainer.getLabel();
            if (label == null) {
                throw new IllegalStateException(
                    "SelfContainedAudioSource must return not null label");
            }
            OptionModel optionModel = OptionModel.key(label);
            for (OptionModel source : audioSourceContainer.getAudioSource()) {
                optionModel.addChild(source);
            }

            optionModels.add(optionModel);
        }

        return optionModels;
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

    @GetMapping("/sink")
    public Collection<OptionModel> getAudioSink() {
        Collection<OptionModel> models = new ArrayList<>();
        for (AudioSink audioSink : audioService.getAudioSinks().values()) {
            for (Map.Entry<String, String> entry : audioSink.getSources().entrySet()) {
                models.add(OptionModel.of(entry.getKey(), entry.getValue()));
            }
        }
        return models;
    }

    @SneakyThrows
    private Path getPlaybackThumbnailPath(String entityID, String fileId, String size) {
        CameraPlaybackStorage entity = context.db().getEntityRequire(entityID);
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

    private ResourceRegion resourceRegion(Resource video, long contentLength, HttpHeaders headers) {
        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1; // Math.min(1024 * 1024, end - start + 1);
            return new ResourceRegion(video, start, rangeLength);
        } else {
            return new ResourceRegion(video, 0, contentLength);
        }
    }

    private ResponseEntity<InputStreamResource> toResponse(ImageResponse response, String eTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("image", "jpeg"));
        headers.add(CACHE_CONTROL, "max-age=31536000, public");
        headers.add(ETAG, eTag);
        headers.add(LAST_MODIFIED, new Date(System.currentTimeMillis()).toString());
        return CommonUtils.inputStreamToResource(response.stream(), response.mediaType(), headers);
    }

    @Getter
    @Setter
    public static class ThumbnailRequest {

        private List<String> fileIds;
    }

    private record MediaPlayContext(@NotNull FileSystemProvider fileSystem, @NotNull String dataSource, @NotNull String id, long size, @NotNull String type) {

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
        BaseCameraEntity<?, ?> entity = context.db().getEntityRequire(entityID);
        if (!entity.getStatus().isOnline()) {
            throw new ServerException("Unable to run execute request. Video entity: %s has wrong status: %s".formatted(entity.getTitle(), entity.getStatus()))
                .setStatus(HttpStatus.PRECONDITION_FAILED);
        }
        return entity;
    }
}

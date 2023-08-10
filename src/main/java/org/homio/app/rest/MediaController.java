package org.homio.app.rest;

import static java.lang.String.format;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.app.manager.common.impl.EntityContextMediaImpl.FFMPEG_LOCATION;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.LAST_MODIFIED;

import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.entity.VideoPlaybackStorage.DownloadFile;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.audio.AudioSink;
import org.homio.api.audio.SelfContainedAudioSourceContainer;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.OptionModel;
import org.homio.api.util.CommonUtils;
import org.homio.app.audio.AudioService;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.ImageService;
import org.homio.app.manager.ImageService.ImageResponse;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
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
public class MediaController {

    private static final Map<String, String> fileIdToLocalStorage = new HashMap<>();
    private final ImageService imageService;
    private final EntityContext entityContext;
    private final AudioService audioService;
    private final FfmpegHardwareRepository ffmpegHardwareRepository;
    private final AddonService addonService;

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

    public static String createVideoLink(String dataSource) {
        String id = "file_" + System.currentTimeMillis();
        fileIdToLocalStorage.put(id, dataSource);
        return "$DEVICE_URL/rest/media/video/" + id + "/play";
    }

    @GetMapping("/video/{fileId}/play")
    public ResponseEntity<ResourceRegion> downloadFile(
        @PathVariable("fileId") String fileId, @RequestHeader HttpHeaders headers)
        throws IOException {
        String source = fileIdToLocalStorage.get(fileId);
        if (source == null) {
            throw new IllegalArgumentException("Unable to find source for fileId: " + fileId);
        }
        Path path = Paths.get(source);

        DownloadFile downloadFile;
        if (Files.exists(path)) {
            downloadFile = new DownloadFile(new UrlResource(path.toUri()), Files.size(path), fileId, null);
        } else {
            throw new IllegalArgumentException("File: " + path + " not exists");
        }

        ResourceRegion region = resourceRegion(downloadFile.stream(), downloadFile.size(), headers);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                             .contentType(MediaTypeFactory.getMediaType(downloadFile.stream()).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(region);
    }

    @GetMapping("/video/playback/days/{entityID}/{from}/{to}")
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(
        @PathVariable("entityID") String entityID,
        @PathVariable(value = "from") @DateTimeFormat(pattern = "yyyyMMdd") Date from,
        @PathVariable(value = "to") @DateTimeFormat(pattern = "yyyyMMdd") Date to)
        throws Exception {
        VideoPlaybackStorage entity = entityContext.getEntityRequire(entityID);
        return entity.getAvailableDaysPlaybacks(entityContext, "main", from, to);
    }

    @GetMapping("/video/playback/files/{entityID}/{date}")
    public List<VideoPlaybackStorage.PlaybackFile> getPlaybackFiles(
        @PathVariable("entityID") String entityID,
        @PathVariable(value = "date") @DateTimeFormat(pattern = "yyyyMMdd") Date date)
        throws Exception {
        VideoPlaybackStorage entity = entityContext.getEntityRequire(entityID);
        return entity.getPlaybackFiles(entityContext, "main", date, new Date(date.getTime() + TimeUnit.DAYS.toMillis(1) - 1));
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
                result.add(entry.getKey() + "~~~");
            } else {
                result.add(entry.getKey() + "~~~data:image/jpg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
            }
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(
        value = "/video/playback/{entityID}/{fileId}/thumbnail/jpg",
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
        VideoPlaybackStorage entity = entityContext.getEntityRequire(entityID);
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
                                   .get(context -> {
                                       log.info("Reply <{}>. Download playback video file <{}>. <{}>", context.getAttemptCount(), entity.getTitle(), fileId);
                                       return entity.downloadPlaybackFile(entityContext, "main", fileId, path);
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

    @GetMapping("/image/{entityID}")
    public ResponseEntity<InputStreamResource> getImage(WebRequest webRequest, @PathVariable String entityID) {
        String eTag = String.valueOf(entityID.hashCode());
        if (webRequest.checkNotModified(eTag)) {
            return null;
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
        VideoPlaybackStorage entity = entityContext.getEntityRequire(entityID);
        Path path = CommonUtils.getMediaPath().resolve("camera").resolve(entityID).resolve("playback")
                               .resolve(fileId + "_" + size.replaceAll(":", "x") + ".jpg");
        if (Files.exists(path) && Files.size(path) > 0) {
            return path;
        }
        CommonUtils.createDirectoriesIfNotExists(path.getParent());
        Files.deleteIfExists(path);

        URI uri = entity.getPlaybackVideoURL(entityContext, fileId);
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

    private void fireFFmpeg(String fileId, String size, VideoPlaybackStorage entity, Path path, String uriStr, ExecutionContext<Path> context) {
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
            long rangeLength = Math.min(1024 * 1024, end - start + 1);
            return new ResourceRegion(video, start, rangeLength);
        } else {
            // long rangeLength = Math.min(1024 * 1024, contentLength);
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
}

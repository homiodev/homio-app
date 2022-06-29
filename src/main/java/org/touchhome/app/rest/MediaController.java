package org.touchhome.app.rest;

import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.audio.AudioService;
import org.touchhome.app.manager.ImageService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.audio.AudioSink;
import org.touchhome.bundle.api.audio.SelfContainedAudioSourceContainer;
import org.touchhome.bundle.api.entity.ImageEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.video.BaseFFMPEGVideoStreamHandler;
import org.touchhome.bundle.api.video.DownloadFile;
import org.touchhome.bundle.api.video.VideoPlaybackStorage;
import org.touchhome.bundle.api.video.ffmpeg.FfmpegInputDeviceHardwareRepository;
import org.touchhome.common.util.CommonUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/rest/media")
@RequiredArgsConstructor
public class MediaController {

    private final ImageService imageService;
    private final EntityContext entityContext;
    private final AudioService audioService;

    private RetryPolicy<Path> PLAYBACK_THUMBNAIL_RETRY_POLICY = RetryPolicy.<Path>builder()
            .handle(Exception.class)
            .withDelay(Duration.ofSeconds(3))
            .withMaxRetries(3)
            .build();

    private RetryPolicy<DownloadFile> PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY =
            RetryPolicy.<DownloadFile>builder()
                    .handle(Exception.class)
                    .withDelay(Duration.ofSeconds(5))
                    .withMaxRetries(3)
                    .build();

    private static final Map<String, String> fileIdToLocalStorage = new HashMap<>();

    public static String createVideoLink(String dataSource) {
        String id = "file_" + System.currentTimeMillis();
        fileIdToLocalStorage.put(id, dataSource);
        return "$DEVICE_URL/rest/media/video/" + id + "/play";
    }

    @GetMapping("/video/{fileId}/play")
    public ResponseEntity<ResourceRegion> downloadFile(@PathVariable("fileId") String fileId,
                                                       @RequestHeader HttpHeaders headers) throws IOException {
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

        ResourceRegion region = resourceRegion(downloadFile.getStream(), downloadFile.getSize(), headers);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory
                        .getMediaType(downloadFile.getStream())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }

    @GetMapping("/video/playback/days/{entityID}/{from}/{to}")
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(@PathVariable("entityID") String entityID,
                                                                  @PathVariable(value = "from")
                                                                  @DateTimeFormat(pattern = "yyyyMMdd") Date from,
                                                                  @PathVariable(value = "to")
                                                                  @DateTimeFormat(pattern = "yyyyMMdd") Date to)
            throws Exception {
        VideoPlaybackStorage entity = entityContext.getEntity(entityID);
        return entity.getAvailableDaysPlaybacks(entityContext, "main", from, to);
    }

    @GetMapping("/video/playback/files/{entityID}/{date}")
    public List<VideoPlaybackStorage.PlaybackFile> getPlaybackFiles(@PathVariable("entityID") String entityID,
                                                                    @PathVariable(value = "date")
                                                                    @DateTimeFormat(pattern = "yyyyMMdd") Date date)
            throws Exception {
        VideoPlaybackStorage entity = entityContext.getEntity(entityID);
        return entity.getPlaybackFiles(entityContext, "main", date, new Date(date.getTime() + TimeUnit.DAYS.toMillis(1) - 1));
    }

    @PostMapping("/video/playback/{entityID}/thumbnails/base64")
    public ResponseEntity<List<String>> getPlaybackThumbnailsBase64(@PathVariable("entityID") String entityID,
                                                                    @RequestBody ThumbnailRequest thumbnailRequest,
                                                                    @RequestParam(value = "size", defaultValue = "800x600")
                                                                    String size) throws Exception {
        Map<String, Path> filePathList = thumbnailRequest.fileIds.stream().sequential().collect(Collectors.toMap(id -> id, id ->
                getPlaybackThumbnailPath(entityID, id, size)));

        Thread.sleep(500); // wait till ffmpeg close all file handlers
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : filePathList.entrySet()) {
            Path filePath = entry.getValue();
            if (filePath == null || !Files.exists(filePath)) {
                result.add(entry.getKey() + "~~~");
            } else {
                result.add(entry.getKey() + "~~~data:image/jpg;base64," + Base64.getEncoder().encodeToString(
                        Files.readAllBytes(filePath)));
            }
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Getter
    @Setter
    public static class ThumbnailRequest {
        private List<String> fileIds;
    }

    @GetMapping(value = "/video/playback/{entityID}/{fileId}/thumbnail/jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getPlaybackThumbnailJpg(@PathVariable("entityID") String entityID,
                                                          @PathVariable("fileId") String fileId,
                                                          @RequestParam(value = "size", defaultValue = "800x600") String size)
            throws Exception {
        Path path = getPlaybackThumbnailPath(entityID, fileId, size);
        return new ResponseEntity<>(path == null ? new byte[0] : Files.readAllBytes(path), HttpStatus.OK);
    }

    @GetMapping("/video/playback/{entityID}/{fileId}/download")
    public ResponseEntity<ResourceRegion> downloadPlaybackFile(@PathVariable("entityID") String entityID,
                                                               @PathVariable("fileId") String fileId,
                                                               @RequestHeader HttpHeaders headers) throws IOException {
        VideoPlaybackStorage entity = entityContext.getEntity(entityID);
        String ext = StringUtils.defaultIfEmpty(FilenameUtils.getExtension(fileId), "mp4");
        Path path = TouchHomeUtils.getMediaPath().resolve("camera").resolve(entityID).resolve("playback")
                .resolve(fileId + "." + ext);

        DownloadFile downloadFile;

        if (Files.exists(path)) {
            downloadFile = new DownloadFile(new UrlResource(path.toUri()), Files.size(path), fileId, null);
        } else {
            downloadFile = Failsafe.with(PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY)
                    .onFailure(event ->
                            log.error("Unable to download playback file: <{}>. <{}>. Msg: <{}>",
                                    entity.getTitle(),
                                    fileId,
                                    CommonUtils.getErrorMessage(event.getFailure())))
                    .get(context -> {
                        log.info("Reply <{}>. Download playback video file <{}>. <{}>", context.getAttemptCount(),
                                entity.getTitle(), fileId);
                        return entity.downloadPlaybackFile(entityContext, "main", fileId, path);
                    });
        }

        ResourceRegion region = resourceRegion(downloadFile.getStream(), downloadFile.getSize(), headers);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory
                        .getMediaType(downloadFile.getStream())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }

    @GetMapping("/audio/{streamId}/play")
    public void playAudioFile(@PathVariable String streamId, HttpServletResponse resp) throws IOException {
        audioService.playRequested(streamId, resp);
    }

    @GetMapping("/image/{imagePath:.+}")
    public ResponseEntity<InputStreamResource> getImage(@PathVariable String imagePath) {
        ImageEntity imageEntity = entityContext.getEntity(imagePath);
        if (imageEntity != null) {
            return getImage(imageEntity.toPath().toString());
        } else {
            return imageService.getImage(imagePath);
        }
    }

    @GetMapping("/audio")
    public Collection<OptionModel> getAudioFiles() {
        return SettingPluginOptionsFileExplorer.getFilePath(TouchHomeUtils.getAudioPath(), 7, false,
                false, true, null, null,
                null, (path, basicFileAttributes) -> {
                    // return new Tika().detect(path).startsWith("audio/");
                    String name = path.getFileName().toString();
                    return name.endsWith(".mp4") || name.endsWith(".wav") || name.endsWith(".mp3");
                }, null,
                path -> path.getFileName() == null ? path.toString() : path.getFileName().toString());
    }

    @GetMapping("/audioSource")
    public Collection<OptionModel> audioSource() {
        Collection<OptionModel> optionModels = new ArrayList<>();
        for (SelfContainedAudioSourceContainer audioSourceContainer : audioService.getAudioSourceContainers()) {
            String label = audioSourceContainer.getLabel();
            if (label == null) {
                throw new IllegalStateException("SelfContainedAudioSource must return not null label");
            }
            OptionModel optionModel = OptionModel.key(label);
            for (OptionModel source : audioSourceContainer.getAudioSource()) {
                optionModel.addChild(source);
            }

            optionModels.add(optionModel);
        }

        return optionModels;
    }

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
        VideoPlaybackStorage entity = entityContext.getEntity(entityID);
        Path path = TouchHomeUtils.getMediaPath().resolve("camera").resolve(entityID).resolve("playback")
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
                        log.error("Unable to get playback img: <{}>. Msg: <{}>",
                                entity.getTitle(),
                                CommonUtils.getErrorMessage(event.getFailure())))
                .get(context -> {
                    log.info("Reply <{}>. playback img <{}>. <{}>", context.getAttemptCount(), entity.getTitle(), fileId);
                    entityContext.getBean(FfmpegInputDeviceHardwareRepository.class).fireFfmpeg(
                            BaseFFMPEGVideoStreamHandler.getFfmpegLocation(),
                            "-y",
                            "\"" + uriStr + "\"",
                            "-frames:v 1 -vf scale=" + size + " -q:v 3 " + path, // q:v - jpg quality
                            60);
                    return path;
                });
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
}

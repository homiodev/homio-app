package org.homio.app.rest;

import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.CameraPlaybackStorage.DownloadFile;
import org.homio.api.AddonEntrypoint;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.video.BaseStreamEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.stream.ContentStream;
import org.homio.api.stream.StreamFormat;
import org.homio.api.stream.audio.AudioPlayer;
import org.homio.api.stream.impl.URLContentStream;
import org.homio.api.stream.video.VideoPlayer;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader.DynamicOptionLoaderParameters;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.ImageService;
import org.homio.app.manager.ImageService.ImageResponse;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextMediaVideoImpl;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity.VideoSeriesDataSourceDynamicOptionLoader;
import org.homio.app.rest.FileSystemController.NodeRequest;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;
import static org.springframework.http.HttpHeaders.ACCEPT_RANGES;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.CONTENT_RANGE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.LAST_MODIFIED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PARTIAL_CONTENT;

@Log4j2
@RestController
@RequestMapping(value = "/rest/media", produces = "application/json")
@RequiredArgsConstructor
public class MediaController implements ContextCreated {

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

  @Override
  public void onContextCreated(ContextImpl context) throws Exception {
    context.bgp().builder("audio-stream-cleanup")
      .intervalWithDelay(Duration.ofMinutes(60))
      .execute(this::removeTimedOutStreams);
  }

  @SneakyThrows
  @GetMapping("/stream/{streamID}/download")
  public ResponseEntity<InputStreamResource> downloadStream(@PathVariable String streamID,
                                                            @RequestParam(value = "start", defaultValue = "-1") double start,
                                                            @RequestParam(value = "end", defaultValue = "-1") double end) {
    StreamContext streamContext = streams.get(streamID);
    if (streamContext == null) {
      return ResponseEntity.notFound().build();
    }
    MimeType mimeType = streamContext.getMediaType();
    MediaType mediaType = new MediaType(mimeType);
    Resource resource = streamContext.stream.getResource();

    InputStream inputStream;
    Integer contentLength = null;
    if ((end > 0 || start > 0) && mimeType.getType().equals("video")) {
      inputStream = getTrimVideoInputStream(resource, start, end, mediaType);
      if (inputStream == null) {
        return ResponseEntity.internalServerError().build();
      }
    } else {
      contentLength = Math.toIntExact(resource.contentLength());
      inputStream = resource.getInputStream();
    }

    return CommonUtils.inputStreamToResource(inputStream, mediaType,
      null, resource.getFilename(), contentLength);
  }

  @SneakyThrows
  @GetMapping("/stream/{streamID}")
  public ResponseEntity<ResourceRegion> transferStream(@PathVariable String streamID, @RequestHeader HttpHeaders headers) {
    StreamContext streamContext = streams.get(streamID);
    if (streamContext == null) {
      return ResponseEntity.notFound().build();
    }

    streamContext.lastRequested = System.currentTimeMillis();
    MimeType mimeType = streamContext.getMediaType();
    Resource resource = streamContext.stream.getResource();

    try {
      return resourceRegion(resource, resource.contentLength(), headers, mimeType);
    } catch (IOException e) {
      log.error("Error streaming the resource: {}", e.getMessage());
    }
    return ResponseEntity.internalServerError().build();
  }

  @GetMapping("/video/{entityID}/sources")
  public List<OptionModel> getVideoSources(@PathVariable("entityID") String entityID) {
    BaseEntity baseEntity = getEntity(entityID);
    var parameters = new DynamicOptionLoaderParameters(baseEntity, context, new String[0], null);
    List<OptionModel> models = new VideoSeriesDataSourceDynamicOptionLoader().loadOptions(parameters);
    return models.isEmpty() ? models : models.get(0).getChildren();
  }

  @SneakyThrows
  @PostMapping("/streamCast")
  public void startChromecast(@RequestBody ChromecastRequest request) {
    BaseStreamEntity entity = context.db().getRequire(request.entityID);
    StreamFormat streamFormat = StreamFormat.evaluateFormat(request.mimeType);
    ContentStream stream = new URLContentStream(new URL(request.url), streamFormat);
    entity.getStreamPlayer().play(stream, null, null);
    // entity.getService().playMedia(request.title, request.url, request.mimeType);
  }

  @DeleteMapping("/streamCast")
  public void stopChromecast(@RequestBody ChromecastRequest request) {
    BaseStreamEntity entity = context.db().getRequire(request.entityID);
    entity.getStreamPlayer().stop();
  }

  @PostMapping("/{entityID}/{provider}/video.webrtc")
  public ResponseEntity<?> postVideoWebRTC(@PathVariable("entityID") String entityID,
                                           @PathVariable("provider") String provider,
                                           HttpServletRequest request, ProxyExchange<byte[]> proxy) {
    Integer port = ContextMediaVideoImpl.webRTCProviders.get(provider);
    if (port == null) {
      throw new IllegalArgumentException("Unable to find webrtc provider: " + provider);
    }
    return proxyUrl(proxy, port, entityID, "whep", request.getQueryString(), ProxyExchange::post);
  }

  @PatchMapping("/{entityID}/mediamtx/video.webrtc")
  public ResponseEntity<?> patchWebRTC(@PathVariable("entityID") String ignore) {
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @GetMapping("/{entityID}/{provider}/{filename}.m3u8")
  public ResponseEntity<?> getMediaHls(@PathVariable("entityID") String entityID,
                                       @PathVariable("provider") String provider,
                                       @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
    Integer port = ContextMediaVideoImpl.hlsProviders.get(provider);
    if (port == null) {
      throw new IllegalArgumentException("Unable to find webrtc provider: " + provider);
    }
    return proxyUrl(proxy, port, entityID, filename + ".m3u8", request.getQueryString(), ProxyExchange::get);
  }

  @GetMapping("/{entityID}/{provider}/{filename}.mp4")
  public ResponseEntity<?> getHlsMp4(@PathVariable("entityID") String entityID,
                                     @PathVariable("provider") String provider,
                                     @PathVariable("filename") String filename, HttpServletRequest request, ProxyExchange<byte[]> proxy) {
    Integer port = ContextMediaVideoImpl.webRTCProviders.get(provider);
    if (port == null) {
      throw new IllegalArgumentException("Unable to find webrtc provider: " + provider);
    }
    return proxyUrl(proxy, port, entityID, filename + ".mp4", request.getQueryString(), ProxyExchange::get);
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

    return new ResponseEntity<>(result, OK);
  }

  @GetMapping(value = "/video/playback/{entityID}/{fileId}/thumbnail/jpg",
    produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> getPlaybackThumbnailJpg(
    @PathVariable("entityID") String entityID,
    @PathVariable("fileId") String fileId,
    @RequestParam(value = "size", defaultValue = "800x600") String size)
    throws Exception {
    Path path = getPlaybackThumbnailPath(entityID, fileId, size);
    return new ResponseEntity<>(path == null ? new byte[0] : Files.readAllBytes(path), OK);
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
    return toResponse(imageService.getImage(entityID), eTag, entityID);
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
    return toResponse(new ImageResponse(stream, MediaType.IMAGE_PNG), eTag, addonID);
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
    return toResponse(imageService.getAddonImage(addonID, imageID), eTag, addonID);
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
    HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
    ResourceRegion region;
    HttpStatusCode statusCode = PARTIAL_CONTENT;
    if (range == null) {
      statusCode = OK;
      region = new ResourceRegion(resource, 0, contentLength);
    } else if (range.toString().equals("0-1")) {
      return ResponseEntity
        .status(PARTIAL_CONTENT)
        .eTag("a03275-5b2180c08fb1f")
        .header(ACCEPT_RANGES, "bytes")
        .header(CONTENT_RANGE, "bytes 0-" + (contentLength - 1) + '/' + contentLength)
        .contentType((MediaType) mimeType)
        .contentLength(contentLength)
        .build();
    } else {
      long start = range.getRangeStart(contentLength);
      long end = range.getRangeEnd(contentLength);
      long rangeLength = end - start + 1; // Math.min(1024 * 1024, end - start + 1);
      region = new ResourceRegion(resource, start, rangeLength);
    }

    return ResponseEntity
      .status(statusCode)
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
      .header(ACCEPT_RANGES, "bytes")
      .contentType((MediaType) mimeType)
      .contentLength(contentLength)
      .body(region);
  }

  @SneakyThrows
  private ResponseEntity<InputStreamResource> toResponse(ImageResponse response, String eTag, String fileName) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("image", "jpeg"));
    headers.add(CACHE_CONTROL, "max-age=31536000, public");
    headers.add(ETAG, eTag);
    headers.add(LAST_MODIFIED, new Date(System.currentTimeMillis()).toString());
    InputStream inputStream = response.stream();
    return CommonUtils.inputStreamToResource(inputStream, response.mediaType(), headers, fileName, inputStream.available());
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
    if (stream.getStreamFormat().getMimeType().toString().equals("application/x-mpegurl")) {
      streamId += ".m3u8";
    }
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

  @SneakyThrows
  private InputStream getTrimVideoInputStream(Resource resource, double start, double end, MediaType mediaType) {
    Path trimFile = CommonUtils.getTmpPath().resolve("trim_stream." + mediaType.getSubtype());
    File fullFile = null;
    boolean deleteFile = false;
    Files.deleteIfExists(trimFile);
    try {
      if (resource.isFile()) {
        fullFile = resource.getFile();
      } else {
        fullFile = CommonUtils.getTmpPath().resolve("stream." + mediaType.getSubtype()).toFile();
        deleteFile = true;
        Files.copy(resource.getInputStream(), fullFile.toPath(), REPLACE_EXISTING);
      }
      context.media().fireFfmpeg("",
        fullFile.getAbsolutePath(), "-ss " + start + " -to " + end + " -c:v copy -c:a copy \"" + trimFile + "\"", 600);
      if (Files.exists(trimFile) && Files.size(trimFile) > 0) {
        return Files.newInputStream(trimFile);
      }
    } finally {
      if (deleteFile) {
        FileUtils.deleteQuietly(fullFile);
      }
    }
    return null;
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

  @Getter
  @Setter
  public static class ChromecastRequest {
    private String url;
    private String mimeType;
    private String entityID;
    private String title;
  }
}

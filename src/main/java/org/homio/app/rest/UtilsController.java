package org.homio.app.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static java.lang.String.format;
import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.springframework.http.HttpHeaders.ORIGIN;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.homio.api.ContextUI;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.console.FileManagerConsolePlugin;
import org.homio.app.js.assistant.impl.CodeParser;
import org.homio.app.js.assistant.impl.ParserContext;
import org.homio.app.js.assistant.model.Completion;
import org.homio.app.js.assistant.model.CompletionRequest;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.impl.extra.WidgetFrameEntity;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
@Validated
public class UtilsController {

  public static final Map<String, OtaLink> otaRequests = new ConcurrentHashMap<>();
  private static final LoadingCache<String, GitHubReadme> readmeCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(1, TimeUnit.HOURS)
          .build(
              new CacheLoader<>() {
                public @NotNull GitHubReadme load(@NotNull String url) {
                  return new GitHubReadme(
                      url, Curl.get(url + "/raw/master/README.md", String.class));
                }
              });
  private final FileSystemController fileSystemController;
  private final ContextImpl context;
  private final ScriptService scriptService;
  private final CodeParser codeParser;

  @PostConstruct
  public void init() {
    context
        .bgp()
        .addLowPriorityRequest(
            "drop-expire-links", () -> otaRequests.values().removeIf(OtaLink::isLinkExpired));
  }

  @SneakyThrows
  @DeleteMapping("/ota")
  public void deleteOneTimeAccessUrl(
      @RequestParam("requestId") String requestId, HttpServletResponse response) {
    var oneTimeRequest = otaRequests.remove(requestId);
    if (oneTimeRequest == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @SneakyThrows
  @GetMapping("/ota")
  public void getOneTimeAccessUrl(
      @RequestParam("requestId") String requestId,
      HttpServletRequest request,
      HttpServletResponse response) {
    var oneTimeRequest = otaRequests.get(requestId);
    if (oneTimeRequest == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    oneTimeRequest.requestedCount++;

    if (oneTimeRequest.maxRequests <= oneTimeRequest.requestedCount) {
      otaRequests.remove(requestId);
      context.ui().console().refreshPluginContent(FileManagerConsolePlugin.NAME);
    } else if (oneTimeRequest.isLinkExpired()) {
      otaRequests.remove(requestId);
      context.ui().console().refreshPluginContent(FileManagerConsolePlugin.NAME);
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (oneTimeRequest.url != null) {
      SecurityContextHolder.getContext().setAuthentication(oneTimeRequest.authentication);
      request.getRequestDispatcher("/" + oneTimeRequest.url).forward(request, response);
    } else {
      var responseEntity =
          fileSystemController.downloadGet(
              oneTimeRequest.sourceFs, oneTimeRequest.id, oneTimeRequest.alias);
      if (responseEntity != null) {
        response.setStatus(responseEntity.getStatusCode().value());
        responseEntity
            .getHeaders()
            .forEach((key, values) -> values.forEach(value -> response.addHeader(key, value)));
        InputStreamResource body = responseEntity.getBody();

        if (body != null) {
          try (InputStream in = body.getInputStream();
              OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
          }
        }
      }
    }
  }

  @PutMapping("/ota")
  public OneTimeUrlResponse createOneTimeAccessLink(
      HttpServletRequest httpRequest, @RequestBody OtaRequest request) {
    String requestId = CommonUtils.generateUUID();
    if ("https://homio.org".equals(httpRequest.getHeader(ORIGIN))) {
      requestId = Objects.toString(httpRequest.getHeader("X-Ota-Id"), requestId);
    }
    // remove previous request with same fields if exists
    otaRequests.values().removeIf(entry -> entry.sameRequest(request));
    otaRequests.put(
        requestId,
        new OtaLink(
            request.sourceFs,
            request.id,
            request.alias,
            request.url,
            request.maxRequests,
            request.expireTimeout,
            SecurityContextHolder.getContext().getAuthentication()));
    return new OneTimeUrlResponse(requestId);
  }

  @GetMapping("/frame/{entityID}")
  public String getFrame(@PathVariable("entityID") String entityID) {
    WidgetFrameEntity widgetFrameEntity = context.db().getRequire(entityID);
    return widgetFrameEntity.getFrame();
  }

  @PostMapping("/github/readme")
  public GitHubReadme getUrlContent(@RequestBody String url) {
    try {
      return readmeCache.get(
          url.endsWith("/wiki") ? url.substring(0, url.length() - "/wiki".length()) : url);
    } catch (Exception ex) {
      throw new ServerException("No readme found");
    }
  }

  @PostMapping("/getCompletions")
  public Set<Completion> getCompletions(@RequestBody CompletionRequest completionRequest)
      throws NoSuchMethodException {
    ParserContext context = ParserContext.noneContext();
    return codeParser.addCompetitionFromManagerOrClass(
        CodeParser.removeAllComments(completionRequest.getLine()),
        new Stack<>(),
        context,
        completionRequest.getAllScript());
  }

  @GetMapping(value = "/download/tmp/{fileName:.+}", produces = APPLICATION_OCTET_STREAM)
  public ResponseEntity<StreamingResponseBody> downloadFile(
      @PathVariable("fileName") String fileName) {
    UserGuestEntity.assertAction(
        context, UserGuestEntity::getDeleteWidget, "User is not allowed to access to FileManager");
    Path outputPath = CommonUtils.getTmpPath().resolve(fileName);
    if (!Files.exists(outputPath)) {
      throw NotFoundException.fileNotFound(outputPath);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.CONTENT_DISPOSITION,
        format("attachment; filename=\"%s\"", outputPath.getFileName()));
    headers.add(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

    return new ResponseEntity<>(
        outputStream -> {
          try (FileChannel inChannel = FileChannel.open(outputPath, StandardOpenOption.READ)) {
            long size = inChannel.size();
            WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
            inChannel.transferTo(0, size, writableByteChannel);
          }
        },
        headers,
        HttpStatus.OK);
  }

  @PostMapping("/code/run")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public RunScriptResponse runScriptOnce(@RequestBody RunScriptRequest request) throws IOException {
    RunScriptResponse runScriptResponse = new RunScriptResponse();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream logOutputStream = new PrintStream(outputStream);
    ScriptEntity scriptEntity = new ScriptEntity();
    scriptEntity.setEntityID(request.entityID);
    scriptEntity.setJavaScript(request.javaScript);
    scriptEntity.setJavaScriptParameters(request.javaScriptParameters);

    try {
      runScriptResponse.result =
          scriptService
              .executeJavaScriptOnce(
                  scriptEntity, logOutputStream, false, State.of(request.contextParameters))
              .stringValue();
    } catch (Exception ex) {
      runScriptResponse.error = ExceptionUtils.getStackTrace(ex);
    }
    int size = outputStream.size();
    if (size > 50000) {
      String name = scriptEntity.getEntityID() + "_size_" + outputStream.size() + "___.log";
      Path tempFile = CommonUtils.getTmpPath().resolve(name);
      Files.copy(tempFile, outputStream);
      runScriptResponse.logUrl =
          "rest/download/tmp/" + CommonUtils.getTmpPath().relativize(tempFile);
    } else {
      runScriptResponse.log = outputStream.toString(StandardCharsets.UTF_8);
    }

    return runScriptResponse;
  }

  @GetMapping("/i18n/{lang}.json")
  @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
  public ObjectNode getI18NLangNodes(@PathVariable("lang") String lang) {
    return Lang.getLangJson(lang);
  }

  @SneakyThrows
  @PostMapping("/header/dialog/{entityID}")
  public void acceptDialog(
      @PathVariable("entityID") String entityID, @RequestBody DialogRequest dialogRequest) {
    context
        .ui()
        .handleDialog(
            entityID,
            ContextUI.DialogResponseType.Accepted,
            dialogRequest.pressedButton,
            OBJECT_MAPPER.readValue(dialogRequest.params, ObjectNode.class));
  }

  @DeleteMapping("/header/dialog/{entityID}")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public void discardDialog(@PathVariable("entityID") String entityID) {
    context.ui().handleDialog(entityID, ContextUI.DialogResponseType.Cancelled, null, null);
  }

  @Getter
  @Setter
  public static class DialogRequest {

    private String pressedButton;
    private String params;
  }

  @Getter
  @AllArgsConstructor
  public static class GitHubReadme {

    private String url;
    private String content;
  }

  @Setter
  public static class RunScriptRequest {

    private String javaScriptParameters;
    private String contextParameters;
    private String entityID;
    private String javaScript;
  }

  @Getter
  public static class RunScriptResponse {

    private Object result;
    private String log;
    private String error;
    private String logUrl;
  }

  @Getter
  @Setter
  public static class OtaRequest {
    private String url;
    private String sourceFs;
    private String id;
    private int alias;
    private int maxRequests;
    private long expireTimeout;
  }

  // expireTimeout(ms) = is time in future when this request has to be expired if no requests
  // maxRequests = how much count this link may be valid before expiry
  @RequiredArgsConstructor
  public static final class OtaLink {
    public final String sourceFs;
    public final String id;
    public final int alias;
    private final long created = System.currentTimeMillis();
    private final String url;
    private final int maxRequests;
    private final long expireTimeout;
    private final Authentication authentication;
    private int requestedCount;

    public boolean isLinkExpired() {
      return expireTimeout > 0 && System.currentTimeMillis() - created > expireTimeout;
    }

    public boolean sameRequest(OtaRequest r) {
      return Objects.equals(r.url, url)
          && Objects.equals(r.sourceFs, sourceFs)
          && Objects.equals(r.id, id)
          && Objects.equals(r.alias, alias);
    }
  }

  public record OneTimeUrlResponse(String id) {}
}

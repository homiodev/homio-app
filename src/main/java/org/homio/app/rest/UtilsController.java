package org.homio.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.validation.Valid;
import lombok.*;
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
import org.homio.app.js.assistant.impl.CodeParser;
import org.homio.app.js.assistant.impl.ParserContext;
import org.homio.app.js.assistant.model.Completion;
import org.homio.app.js.assistant.model.CompletionRequest;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.impl.extra.WidgetFrameEntity;
import org.homio.app.model.rest.DynamicUpdateRequest;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static java.lang.String.format;
import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
@Validated
public class UtilsController {

    private final ContextImpl context;
    private final ScriptService scriptService;
    private final CodeParser codeParser;

    private static final LoadingCache<String, GitHubReadme> readmeCache =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {
                public @NotNull GitHubReadme load(@NotNull String url) {
                    return new GitHubReadme(url, Curl.get(url + "/raw/master/README.md", String.class));
                }
            });

    @PutMapping("/multiDynamicUpdates")
    public void multiDynamicUpdates(@Valid @RequestBody List<DynamicRequestItem> request) {
        for (DynamicRequestItem requestItem : request) {
            context.ui().registerForUpdates(new DynamicUpdateRequest(requestItem.did, requestItem.eid));
        }
    }

    @DeleteMapping("/dynamicUpdates")
    public void unregisterForUpdates(@Valid @RequestBody DynamicUpdateRequest request) {
        context.ui().unRegisterForUpdates(request);
    }

    @GetMapping("/frame/{entityID}")
    public String getFrame(@PathVariable("entityID") String entityID) {
        WidgetFrameEntity widgetFrameEntity = context.db().getRequire(entityID);
        return widgetFrameEntity.getFrame();
    }

    @PostMapping("/github/readme")
    public GitHubReadme getUrlContent(@RequestBody String url) {
        try {
            return readmeCache.get(url.endsWith("/wiki") ? url.substring(0, url.length() - "/wiki".length()) : url);
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
        UserGuestEntity.assertAction(context, new Predicate<UserGuestEntity>() {
            @Override
            public boolean test(UserGuestEntity guest) {
                return guest.getDeleteWidget();
            }
        }, "User is not allowed to access to FileManager");
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
    public RunScriptResponse runScriptOnce(@RequestBody RunScriptRequest request)
            throws IOException {
        RunScriptResponse runScriptResponse = new RunScriptResponse();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logOutputStream = new PrintStream(outputStream);
        ScriptEntity scriptEntity = new ScriptEntity();
        scriptEntity.setEntityID(request.entityID);
        scriptEntity.setJavaScript(request.javaScript);
        scriptEntity.setJavaScriptParameters(request.javaScriptParameters);

        try {
            runScriptResponse.result =
                    scriptService.executeJavaScriptOnce(
                            scriptEntity,
                            logOutputStream,
                            false,
                            State.of(request.contextParameters)).stringValue();
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
    public void acceptDialog(@PathVariable("entityID") String entityID, @RequestBody DialogRequest dialogRequest) {
        context.ui().handleDialog(entityID, ContextUI.DialogResponseType.Accepted, dialogRequest.pressedButton,
                OBJECT_MAPPER.readValue(dialogRequest.params, ObjectNode.class));
    }

    @DeleteMapping("/header/dialog/{entityID}")
    @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
    public void discardDialog(@PathVariable("entityID") String entityID) {
        context.ui().handleDialog(entityID, ContextUI.DialogResponseType.Cancelled, null, null);
    }

    @Getter
    @Setter
    public static class DynamicRequestItem {

        private String eid;
        private String did;
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
}

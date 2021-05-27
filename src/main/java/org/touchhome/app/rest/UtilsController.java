package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.touchhome.app.js.assistant.impl.CodeParser;
import org.touchhome.app.js.assistant.impl.ParserContext;
import org.touchhome.app.js.assistant.model.Completion;
import org.touchhome.app.js.assistant.model.CompletionRequest;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.entity.storage.BaseFileSystemEntity;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.util.Curl;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.Stack;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;
import static org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks.ENTITY;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class UtilsController {

    private final EntityContextImpl entityContext;
    private final ScriptService scriptService;
    private final CodeParser codeParser;

    @PostMapping("/github/readme")
    public GitHubReadme getUrlContent(@RequestBody String url) {
        try {
            if (url.endsWith("/wiki")) {
                url = url.substring(0, url.length() - 5);
            }
            return new GitHubReadme(url, Curl.get(url + "/raw/master/README.md", String.class));
        } catch (Exception ex) {
            throw new ServerException("No readme found");
        }
    }

    @GetMapping("/fs/file")
    public Collection<OptionModel> getFiles(@RequestParam(name = ENTITY, required = false) String fsEntityId) {
        if (fsEntityId != null) {
            BaseFileSystemEntity entity = entityContext.getEntity(fsEntityId);
            if (entity != null) {
                return entity.getFileSystem(entityContext).getAllFiles(true);
            }
        }
        return Collections.emptyList();
    }

    @GetMapping("/fs/folder")
    public Collection<OptionModel> getFolders(@RequestParam(name = ENTITY, required = false) String fsEntityId) {
        if (fsEntityId != null) {
            BaseFileSystemEntity entity = entityContext.getEntity(fsEntityId);
            if (entity != null) {
                return entity.getFileSystem(entityContext).getAllFolders(true);
            }
        }
        return Collections.emptyList();
    }

    @PostMapping("/getCompletions")
    public Set<Completion> getCompletions(@RequestBody CompletionRequest completionRequest) throws NoSuchMethodException {
        ParserContext context = ParserContext.noneContext();
        return codeParser.addCompetitionFromManagerOrClass(
                CodeParser.removeAllComments(completionRequest.getLine()),
                new Stack<>(),
                context,
                completionRequest.getAllScript());
    }

    @GetMapping(value = "/download/tmp/{fileName:.+}", produces = APPLICATION_OCTET_STREAM)
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable("fileName") String fileName) {
        Path outputPath = TouchHomeUtils.fromTmpFile(fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", outputPath.getFileName()));
        headers.add(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        return new ResponseEntity<>(outputStream -> {
            FileChannel inChannel = FileChannel.open(outputPath, StandardOpenOption.READ);
            long size = inChannel.size();
            WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
            inChannel.transferTo(0, size, writableByteChannel);
        },
                headers, HttpStatus.OK);
    }

    @PostMapping("/code/run")
    @Secured(PRIVILEGED_USER_ROLE)
    public RunScriptOnceJSON runScriptOnce(@RequestBody ScriptEntity scriptEntity) throws IOException {
        RunScriptOnceJSON runScriptOnceJSON = new RunScriptOnceJSON();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logOutputStream = new PrintStream(outputStream);
        try {
            runScriptOnceJSON.result = scriptService.executeJavaScriptOnce(scriptEntity, scriptEntity.getJavaScriptParameters(), logOutputStream, false);
        } catch (Exception ex) {
            runScriptOnceJSON.error = ExceptionUtils.getStackTrace(ex);
        }
        int size = outputStream.size();
        if (size > 50000) {
            runScriptOnceJSON.logUrl = TouchHomeUtils.toTmpFile(scriptEntity.getEntityID() + "_size_" + outputStream.size() + "___", ".log", outputStream);
        } else {
            runScriptOnceJSON.log = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }

        return runScriptOnceJSON;
    }

    @GetMapping("/i18n/{lang}.json")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ObjectNode getI18NFromBundles(@PathVariable("lang") String lang) {
        return Lang.getLangJson(lang);
    }

    @PostMapping("/dialog/{entityID}")
    public void acceptDialog(@PathVariable("entityID") String entityID, @RequestBody DialogRequest dialogRequest) {
        entityContext.ui().handleDialog(entityID, EntityContextUI.DialogResponseType.Accepted,
                dialogRequest.pressedButton, new JSONObject(dialogRequest.params));
    }

    @DeleteMapping("/dialog/{entityID}")
    public void discardDialog(@PathVariable("entityID") String entityID) {
        entityContext.ui().handleDialog(entityID, EntityContextUI.DialogResponseType.Cancelled, null, null);
    }

    @Getter
    private static class DialogRequest {
        private String pressedButton;
        private String params;
    }

    @Getter
    @AllArgsConstructor
    private static class GitHubReadme {
        private String url;
        private String content;
    }

    @Getter
    private static class RunScriptOnceJSON {
        private Object result;
        private String log;
        private String error;
        private String logUrl;
    }
}

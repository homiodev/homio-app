package org.touchhome.app.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;
import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Date;
import java.util.Set;
import java.util.Stack;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.touchhome.app.js.assistant.impl.CodeParser;
import org.touchhome.app.js.assistant.impl.ParserContext;
import org.touchhome.app.js.assistant.model.Completion;
import org.touchhome.app.js.assistant.model.CompletionRequest;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Curl;
import org.touchhome.common.util.Lang;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class UtilsController {

    private final EntityContextImpl entityContext;
    private final ScriptService scriptService;
    private final CodeParser codeParser;

    @PutMapping("/dynamicUpdates")
    public void registerForUpdates(@Valid @RequestBody DynamicUpdateRequest request) {
        entityContext.ui().registerForUpdates(request);
    }

    @DeleteMapping("/dynamicUpdates")
    public void unregisterForUpdates(@Valid @RequestBody DynamicUpdateRequest request) {
        entityContext.ui().unRegisterForUpdates(request);
    }

    @GetMapping("/app/config")
    public DeviceConfig getAppConfiguration() {
        DeviceConfig deviceConfig = new DeviceConfig();
        UserEntity userEntity = entityContext.getUser(false);
        deviceConfig.hasKeystore = userEntity.getKeystore() != null;
        deviceConfig.keystoreDate = userEntity.getKeystoreDate();
        return deviceConfig;
    }

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
        Path outputPath = CommonUtils.getTmpPath().resolve(fileName);
        if (!Files.exists(outputPath)) {
            throw new NotFoundException("Unable to find file: " + fileName);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(
                HttpHeaders.CONTENT_DISPOSITION,
                String.format("attachment; filename=\"%s\"", outputPath.getFileName()));
        headers.add(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        return new ResponseEntity<>(
                outputStream -> {
                    FileChannel inChannel = FileChannel.open(outputPath, StandardOpenOption.READ);
                    long size = inChannel.size();
                    WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
                    inChannel.transferTo(0, size, writableByteChannel);
                },
                headers,
                HttpStatus.OK);
    }

    @PostMapping("/code/run")
    @Secured(PRIVILEGED_USER_ROLE)
    public RunScriptOnceJSON runScriptOnce(@RequestBody ScriptEntity scriptEntity)
            throws IOException {
        RunScriptOnceJSON runScriptOnceJSON = new RunScriptOnceJSON();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logOutputStream = new PrintStream(outputStream);
        try {
            runScriptOnceJSON.result =
                    scriptService.executeJavaScriptOnce(
                            scriptEntity,
                            scriptEntity.getJavaScriptParameters(),
                            logOutputStream,
                            false);
        } catch (Exception ex) {
            runScriptOnceJSON.error = ExceptionUtils.getStackTrace(ex);
        }
        int size = outputStream.size();
        if (size > 50000) {
            String name = scriptEntity.getEntityID() + "_size_" + outputStream.size() + "___.log";
            Path tempFile = CommonUtils.getTmpPath().resolve(name);
            Files.copy(tempFile, outputStream);
            runScriptOnceJSON.logUrl =
                    "rest/download/tmp/" + CommonUtils.getTmpPath().relativize(tempFile);
        } else {
            runScriptOnceJSON.log = outputStream.toString(StandardCharsets.UTF_8);
        }

        return runScriptOnceJSON;
    }

    @GetMapping("/i18n/{lang}.json")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ObjectNode getI18NFromBundles(@PathVariable("lang") String lang) {
        return Lang.getLangJson(lang);
    }

    @PostMapping("/notification/{entityID}/action")
    public ActionResponseModel notificationAction(
            @PathVariable("entityID") String entityID,
            @RequestBody HeaderActionRequest actionRequest) {
        try {
            return entityContext
                    .ui()
                    .handleNotificationAction(
                            entityID, actionRequest.entityID, actionRequest.value);
        } catch (Exception ex) {
            throw new IllegalStateException(Lang.getServerMessage(ex.getMessage()));
        }
    }

    @SneakyThrows
    @PostMapping("/header/dialog/{entityID}")
    public void acceptDialog(
            @PathVariable("entityID") String entityID, @RequestBody DialogRequest dialogRequest) {
        entityContext
                .ui()
                .handleDialog(
                        entityID,
                        EntityContextUI.DialogResponseType.Accepted,
                        dialogRequest.pressedButton,
                        OBJECT_MAPPER.readValue(dialogRequest.params, ObjectNode.class));
    }

    @DeleteMapping("/header/dialog/{entityID}")
    public void discardDialog(@PathVariable("entityID") String entityID) {
        entityContext
                .ui()
                .handleDialog(entityID, EntityContextUI.DialogResponseType.Cancelled, null, null);
    }

    @Getter
    @Setter
    private static class HeaderActionRequest {

        private String entityID;
        private String value;
    }

    @Getter
    @Setter
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

    @Getter
    @Setter
    private static class DeviceConfig {

        public final boolean hasUserPassword = true;
        private final boolean bootOnly = false;
        private final boolean hasApp = true;
        private final boolean hasInitSetup = true;
        private boolean hasKeystore;
        private Date keystoreDate;
    }
}

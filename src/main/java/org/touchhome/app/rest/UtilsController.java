package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
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
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.utils.Curl;
import org.touchhome.app.utils.InternalUtil;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.NotificationModel;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.color.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.OneToMany;
import javax.validation.constraints.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.touchhome.bundle.api.util.TouchHomeUtils.PRIVILEGED_USER_ROLE;

@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class UtilsController {

    private final EntityContextImpl entityContext;
    private final ScriptManager scriptManager;
    private final CodeParser codeParser;

    @SneakyThrows
    static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType) {
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>());
    }

    @SneakyThrows
    static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType, Set<EntityUIMetaData> entityUIMetaDataSet) {
        if (entityClassByType == null) {
            return Collections.emptyList();
        }
        Object instance = TouchHomeUtils.newInstance(entityClassByType);
        if (instance == null) {
            throw new NotFoundException("Unable to find empty constructor for class: " + entityClassByType.getName());
        }
        return fillEntityUIMetadataList(instance, entityUIMetaDataSet);
    }

    static List<EntityUIMetaData> fillEntityUIMetadataList(Object instance, Set<EntityUIMetaData> entityUIMetaDataSet) {
        Set<String> foundMethods = new HashSet<>();
        for (Method method : MethodUtils.getMethodsListWithAnnotation(instance.getClass(), UIField.class)) {
            String name = method.getAnnotation(UIField.class).name();
            String methodName = InternalUtil.getMethodShortName(method);
            foundMethods.add(methodName);
            generateUIField(instance, entityUIMetaDataSet, method, StringUtils.defaultIfEmpty(name, methodName),
                    method.getReturnType(), method.getAnnotation(UIField.class), methodName, method.getGenericReturnType());
        }

        FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
            if (!foundMethods.contains(field.getName())) { // skip if already managed by methods
                generateUIField(instance, entityUIMetaDataSet, field,
                        StringUtils.defaultIfEmpty(field.getAnnotation(UIField.class).name(), field.getName()),
                        field.getType(), field.getAnnotation(UIField.class), field.getName(), field.getGenericType());
            }
        });

        List<EntityUIMetaData> data = new ArrayList<>(entityUIMetaDataSet);
        Collections.sort(data);

        return data;
    }

    @SneakyThrows
    private static void generateUIField(Object instance,
                                        Set<EntityUIMetaData> entityUIMetaDataList,
                                        AccessibleObject accessibleObject, String name, Class<?> type,
                                        UIField uiField,
                                        String sourceName,
                                        Type genericType) {
        if (name == null) {
            throw new IllegalArgumentException("generateUIField name must be not null");
        }
        EntityUIMetaData entityUIMetaData = new EntityUIMetaData();
        entityUIMetaData.setEntityName(name);

        if (uiField.transparent()) {
            entityUIMetaDataList.remove(entityUIMetaData);
            return; // skip transparent UIFields
        }

        entityUIMetaData.setLabel(trimToNull(uiField.label()));
        if (uiField.inlineEdit()) {
            entityUIMetaData.setInlineEdit(true);
        }
        entityUIMetaData.setColor(trimToNull(uiField.color()));
        if (uiField.readOnly()) {
            entityUIMetaData.setReadOnly(true);
        }
        if (uiField.hideOnEmpty()) {
            entityUIMetaData.setHideOnEmpty(true);
        }
        if (uiField.onlyEdit()) {
            entityUIMetaData.setOnlyEdit(true);
        }
        if (accessibleObject instanceof Field) {
            entityUIMetaData.setDefaultValue(FieldUtils.readField((Field) accessibleObject, instance, true));
        } else {
            entityUIMetaData.setDefaultValue(((Method) accessibleObject).invoke(instance));
        }

        JSONObject jsonTypeMetadata = new JSONObject();
        if (uiField.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum() ||
                    accessibleObject.isAnnotationPresent(UIFieldSelection.class) ||
                    accessibleObject.isAnnotationPresent(UIFieldClassSelection.class) ||
                    accessibleObject.isAnnotationPresent(UIFieldBeanSelection.class)) {
                entityUIMetaData.setType(uiField.readOnly() ? UIFieldType.String.name() : UIFieldType.Selection.name());
            } else {
                if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                    Type argument = ((ParameterizedType) genericType).getActualTypeArguments()[0];

                    if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
                        entityUIMetaData.setType("List");
                        jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
                        jsonTypeMetadata.put("mappedBy", accessibleObject.getAnnotation(OneToMany.class).mappedBy());
                    }
                } else {
                    if (type.equals(boolean.class)) {
                        type = Boolean.class;
                    } else if (type.equals(float.class)) {
                        type = Float.class;
                    } else if (type.equals(int.class)) {
                        type = Integer.class;
                    }
                    entityUIMetaData.setType(type.getSimpleName());
                }
            }
        } else {
            entityUIMetaData.setType(uiField.type().name());
        }

        Pattern pattern = accessibleObject.getDeclaredAnnotation(Pattern.class);
        if (pattern != null) {
            jsonTypeMetadata.put("regexp", pattern.regexp());
            jsonTypeMetadata.put("regexpMsg", pattern.message());
        }

        UIFieldColorMatch[] uiFieldColorMatches = accessibleObject.getDeclaredAnnotationsByType(UIFieldColorMatch.class);
        if (uiFieldColorMatches.length > 0) {
            JSONObject colors = new JSONObject();
            for (UIFieldColorMatch uiFieldColorMatch : uiFieldColorMatches) {
                colors.put(uiFieldColorMatch.value(), uiFieldColorMatch.color());
            }
            jsonTypeMetadata.put("valueColor", colors);
        }

        UIFieldColorStatusMatch uiFieldColorStatusMatch = accessibleObject.getDeclaredAnnotation(UIFieldColorStatusMatch.class);
        if (uiFieldColorStatusMatch != null) {
            JSONObject colors = new JSONObject();
            colors.put("OFFLINE", uiFieldColorStatusMatch.offline());
            colors.put("ONLINE", uiFieldColorStatusMatch.online());
            colors.put("UNKNOWN", uiFieldColorStatusMatch.unknown());
            colors.put("ERROR", uiFieldColorStatusMatch.error());
            jsonTypeMetadata.put("valueColor", colors);
        }

        UIFieldColorBooleanMatch uiFieldColorBooleanMatch = accessibleObject.getDeclaredAnnotation(UIFieldColorBooleanMatch.class);
        if (uiFieldColorBooleanMatch != null) {
            JSONObject colors = new JSONObject();
            colors.put("true", uiFieldColorBooleanMatch.False());
            colors.put("false", uiFieldColorBooleanMatch.True());
            jsonTypeMetadata.put("valueColor", colors);
        }

        UIFieldColorRef uiFieldColorRef = accessibleObject.getDeclaredAnnotation(UIFieldColorRef.class);
        if (uiFieldColorRef != null) {
            if (instance.getClass().getDeclaredField(uiFieldColorRef.value()) == null) {
                throw new ServerException("Unable to find field <" + uiFieldColorRef.value() + "> declared in UIFieldColorRef");
            }
            jsonTypeMetadata.put("colorRef", uiFieldColorRef.value());
        }

        UIFieldExpand uiFieldExpand = accessibleObject.getDeclaredAnnotation(UIFieldExpand.class);
        if (uiFieldExpand != null && type.isAssignableFrom(List.class)) {
            jsonTypeMetadata.put("expand", "true");
        }

        UIFieldColorSource uiFieldRowColor = accessibleObject.getDeclaredAnnotation(UIFieldColorSource.class);
        if (uiFieldRowColor != null) {
            jsonTypeMetadata.put("rc", sourceName);
        }

        UIFieldCreateWorkspaceVariableOnEmpty uiFieldCreateWorkspaceVariable = accessibleObject.getDeclaredAnnotation(UIFieldCreateWorkspaceVariableOnEmpty.class);
        if (uiFieldCreateWorkspaceVariable != null) {
            jsonTypeMetadata.put("cwvoe", "true");
        }

        UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty = accessibleObject.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
        if (uiFieldSelectValueOnEmpty != null) {
            jsonTypeMetadata.put("sveColor", uiFieldSelectValueOnEmpty.color());
            jsonTypeMetadata.put("sveLabel", uiFieldSelectValueOnEmpty.label());
        }

        if (entityUIMetaData.getType().equals(String.class.getSimpleName())) {
            UIKeyValueField uiKeyValueField = accessibleObject.getDeclaredAnnotation(UIKeyValueField.class);
            if (uiKeyValueField != null) {
                jsonTypeMetadata.put("maxSize", uiKeyValueField.maxSize());
                jsonTypeMetadata.put("keyType", uiKeyValueField.keyType());
                jsonTypeMetadata.put("valueType", uiKeyValueField.valueType());
                jsonTypeMetadata.put("defaultKey", uiKeyValueField.defaultKey());
                jsonTypeMetadata.put("defaultValue", uiKeyValueField.defaultValue());
                jsonTypeMetadata.put("keyFormat", uiKeyValueField.keyFormat());
                jsonTypeMetadata.put("valueFormat", uiKeyValueField.valueFormat());
                jsonTypeMetadata.put("keyValueType", uiKeyValueField.keyValueType().name());
                entityUIMetaData.setType("KeyValue");
            }
        }

        UIFieldNumber uiFieldNumber = accessibleObject.getDeclaredAnnotation(UIFieldNumber.class);
        if (uiFieldNumber != null) {
            jsonTypeMetadata.put("min", uiFieldNumber.min());
            jsonTypeMetadata.put("max", uiFieldNumber.max());
        }

        if (accessibleObject.isAnnotationPresent(UIFieldSlider.class)) {
            UIFieldSlider uiFieldSlider = accessibleObject.getDeclaredAnnotation(UIFieldSlider.class);
            jsonTypeMetadata.put("min", uiFieldSlider.min());
            jsonTypeMetadata.put("max", uiFieldSlider.max());
            jsonTypeMetadata.put("step", uiFieldSlider.step());
            entityUIMetaData.setType(UIFieldType.Slider.name());
        }

        if (accessibleObject.isAnnotationPresent(UIFieldCodeEditor.class)) {
            UIFieldCodeEditor uiFieldCodeEditor = accessibleObject.getDeclaredAnnotation(UIFieldCodeEditor.class);
            jsonTypeMetadata.put("autoFormat", uiFieldCodeEditor.autoFormat());
            jsonTypeMetadata.put("editorType", uiFieldCodeEditor.editorType());
            entityUIMetaData.setType("CodeEditor");
        }

        if (uiField.showInContextMenu() && entityUIMetaData.getType().equals(Boolean.class.getSimpleName())) {
            entityUIMetaData.setShowInContextMenu(true);
        }
        if (jsonTypeMetadata.length() != 0) {
            entityUIMetaData.setTypeMetaData(jsonTypeMetadata.toString());
        }
        entityUIMetaData.setOrder(uiField.order());
        if (uiField.required()) {
            entityUIMetaData.setRequired(true);
        }
        if (BaseEntity.class.isAssignableFrom(type) && type.getDeclaredAnnotation(UISidebarMenu.class) != null) {
            entityUIMetaData.setNavLink("/client/items/" + type.getSimpleName());
        }

        entityUIMetaDataList.remove(entityUIMetaData);
        entityUIMetaDataList.add(entityUIMetaData);
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

    @GetMapping("/notifications")
    public Set<NotificationModel> getNotifications() {
        return entityContext.ui().getNotifications();
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
            runScriptOnceJSON.result = scriptManager.executeJavaScriptOnce(scriptEntity, scriptEntity.getJavaScriptParameters(), logOutputStream, false);
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

    @PostMapping("/confirm/{entityID}")
    public void confirm(@PathVariable("entityID") String entityID) {
        EntityContextUIImpl.ConfirmationRequestModel confirmationRequestModel = EntityContextUIImpl.confirmationRequest.get(entityID);
        if (confirmationRequestModel != null && !confirmationRequestModel.isHandled()) {
            confirmationRequestModel.getHandler().run();
            confirmationRequestModel.setHandled(true);
        }
    }

    @DeleteMapping("/confirm/{entityID}")
    public void notConfirm(@PathVariable("entityID") String entityID) {
        if (EntityContextUIImpl.confirmationRequest.containsKey(entityID)) {
            EntityContextUIImpl.confirmationRequest.get(entityID).setHandled(true);
        }
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

package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fazecast.jSerialComm.SerialPort;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.touchhome.app.js.assistant.impl.CodeParser;
import org.touchhome.app.js.assistant.impl.ParserContext;
import org.touchhome.app.js.assistant.model.Completion;
import org.touchhome.app.js.assistant.model.CompletionRequest;
import org.touchhome.app.json.ScriptUiGroupsJSON;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.manager.scripting.ScriptManager;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.manager.En;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.api.ui.method.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.OneToMany;
import java.beans.Introspector;
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
import java.util.stream.Stream;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.commons.lang.StringUtils.trimToNull;

@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class UtilsController {

    private final InternalManager entityContext;
    private final ScriptManager scriptManager;
    private final CodeParser codeParser;
    private final Map<String, AbstractRepository> repositories;

    @SneakyThrows
    static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType) {
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>());
    }

    @SneakyThrows
    static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType, Set<EntityUIMetaData> entityUIMetaDataSet) {
        Constructor constructor = Stream.of(entityClassByType.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 0).findAny()
                .orElseThrow(() -> new NotFoundException("Unable to find empty constructor for class: " + entityClassByType.getName()));
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();

        FieldUtils.getFieldsListWithAnnotation(entityClassByType, UIField.class).forEach(field ->
                generateUIField(instance, entityUIMetaDataSet, field, StringUtils.defaultIfEmpty(field.getAnnotation(UIField.class).name(), field.getName()), field.getType(), field.getAnnotation(UIField.class)));

        for (Method method : MethodUtils.getMethodsListWithAnnotation(entityClassByType, UIField.class)) {
            String name = method.getAnnotation(UIField.class).name();
            if (StringUtils.isEmpty(name)) {
                //not too safe ))
                name = Introspector.decapitalize(method.getName().substring(method.getName().startsWith("is") ? 2 : 3));
            }

            generateUIField(instance, entityUIMetaDataSet, null, name, method.getReturnType(), method.getAnnotation(UIField.class));
        }

        List<EntityUIMetaData> data = new ArrayList<>(entityUIMetaDataSet);
        Collections.sort(data);

        return data;
    }

    @SneakyThrows
    private static void generateUIField(Object instance,
                                        Set<EntityUIMetaData> entityUIMetaDataList,
                                        Field field, String name, Class type,
                                        UIField uiField) {
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
        if (field != null) {
            entityUIMetaData.setDefaultValue(FieldUtils.readField(field, instance, true));
        }
        JSONObject jsonTypeMetadata = new JSONObject();
        if (uiField.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum() || (field != null && field.isAnnotationPresent(UIFieldTargetSelection.class))) {
                entityUIMetaData.setType(UIFieldType.Selection.name());
            } else {
                if (field != null && field.getGenericType() instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                    Type genericType = field.getGenericType();
                    Type argument = ((ParameterizedType) genericType).getActualTypeArguments()[0];

                    if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
                        entityUIMetaData.setType("List");
                        jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
                        jsonTypeMetadata.put("mappedBy", field.getAnnotation(OneToMany.class).mappedBy());
                    } else {
                        entityUIMetaData.setType(UIFieldType.PlainList.name());
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

        if (field != null) {
            UIFieldColorMatch[] uiFieldColorMatches = field.getDeclaredAnnotationsByType(UIFieldColorMatch.class);
            if (uiFieldColorMatches.length > 0) {
                JSONObject colors = new JSONObject();
                for (UIFieldColorMatch uiFieldColorMatch : uiFieldColorMatches) {
                    colors.put(uiFieldColorMatch.value(), uiFieldColorMatch.color());
                }
                jsonTypeMetadata.put("valueColor", colors);
            }

            UIFieldColorRef uiFieldColorRef = field.getDeclaredAnnotation(UIFieldColorRef.class);
            if (uiFieldColorRef != null) {
                if (instance.getClass().getDeclaredField(uiFieldColorRef.value()) == null) {
                    throw new RuntimeException("Unable to find field <" + uiFieldColorRef.value() + "> declared in UIFieldColorRef");
                }
                jsonTypeMetadata.put("colorRef", uiFieldColorRef.value());
            }

            UIFieldExpand uiFieldExpand = field.getDeclaredAnnotation(UIFieldExpand.class);
            if (uiFieldExpand != null && field.getType().isAssignableFrom(List.class)) {
                jsonTypeMetadata.put("expand", "true");
            }

            UIFieldRowColor uiFieldRowColor = field.getDeclaredAnnotation(UIFieldRowColor.class);
            if (uiFieldRowColor != null) {
                jsonTypeMetadata.put("rc", field.getName());
            }

            UIFieldCreateWorkspaceVariableOnEmpty uiFieldCreateWorkspaceVariable = field.getDeclaredAnnotation(UIFieldCreateWorkspaceVariableOnEmpty.class);
            if (uiFieldCreateWorkspaceVariable != null) {
                jsonTypeMetadata.put("cwvoe", "true");
            }

            UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty = field.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
            if (uiFieldSelectValueOnEmpty != null) {
                TouchHomeUtils.findRequreMethod(instance.getClass(), uiFieldSelectValueOnEmpty.method());
                jsonTypeMetadata.put("sveColor", uiFieldSelectValueOnEmpty.color());
                jsonTypeMetadata.put("sveLabel", uiFieldSelectValueOnEmpty.label());
                jsonTypeMetadata.put("selectOptionMethod", uiFieldSelectValueOnEmpty.method());
            }

            if (entityUIMetaData.getType().equals(String.class.getSimpleName())) {
                UIKeyValueField uiKeyValueField = field.getDeclaredAnnotation(UIKeyValueField.class);
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

                UIFieldTextWithSelection uiFieldTextWithSelection = field.getDeclaredAnnotation(UIFieldTextWithSelection.class);
                if (uiFieldTextWithSelection != null) {
                    TouchHomeUtils.findRequreMethod(instance.getClass(), uiFieldTextWithSelection.method());
                    jsonTypeMetadata.put("selectOptionMethod", uiFieldTextWithSelection.method());
                    entityUIMetaData.setType("TextSelectBoxDynamic");
                }
            }

            if (entityUIMetaData.getType().equals(String.class.getSimpleName()) || entityUIMetaData.getType().equals(UIFieldType.Slider.name())) {
                UIFieldNumber uiFieldNumber = field.getDeclaredAnnotation(UIFieldNumber.class);
                if (uiFieldNumber != null) {
                    jsonTypeMetadata.put("min", uiFieldNumber.min());
                    jsonTypeMetadata.put("max", uiFieldNumber.max());
                }
            }

            if (field.isAnnotationPresent(UIFieldCodeEditor.class)) {
                UIFieldCodeEditor uiFieldCodeEditor = field.getDeclaredAnnotation(UIFieldCodeEditor.class);
                jsonTypeMetadata.put("autoFormat", uiFieldCodeEditor.autoFormat());
                jsonTypeMetadata.put("editorType", uiFieldCodeEditor.editorType());
                entityUIMetaData.setType("CodeEditor");
            }
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

    @GetMapping("notifications")
    public Set<NotificationEntityJSON> getNotifications() {
        return entityContext.getNotifications();
    }

    @PostMapping("getCompletions")
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

    @PostMapping("code/run")
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

    // TODO: not implemented
    @GetMapping("/getAllItemsForScript")
    public ScriptUiGroupsJSON getAllItemsForScript() {
        ScriptUiGroupsJSON scriptUiGroupsJSON = new ScriptUiGroupsJSON();
        for (AbstractRepository repository : repositories.values()) {
            Class<?> clazz = repository.getEntityClass();
            if (clazz.isAnnotationPresent(UISidebarMenu.class)) {
                ScriptUiGroupsJSON.Group group = scriptUiGroupsJSON.addGroup(clazz.getSimpleName());
                Class<?> targetClass = AopUtils.getTargetClass(repository);
                group.baseCode = "context.get" + targetClass.getSimpleName() + "().getByEntityID(";

                List<BaseEntity> list = repository.listAll();
                for (BaseEntity baseEntity : list) {
                    group.add(baseEntity);
                }
            }
        }
        return scriptUiGroupsJSON;
    }

    @GetMapping("/getComPorts")
    public List<ComPort> getComPorts() {
        List<ComPort> comPorts = new ArrayList<>();
        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            ComPort comPort = new ComPort();
            comPort.setBaudRate(serialPort.getBaudRate());
            comPort.setDescriptivePortName(serialPort.getDescriptivePortName());
            comPort.setSystemPortName(serialPort.getSystemPortName());
            comPorts.add(comPort);
        }
        return comPorts;
    }

    @GetMapping("i18n/{lang}.json")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ObjectNode getI18NFromBundles(@PathVariable("lang") String lang) {
        return En.get().getLangJson(lang);
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
    private class ComPort {
        private int baudRate;
        private String descriptivePortName;
        private String systemPortName;
    }
}

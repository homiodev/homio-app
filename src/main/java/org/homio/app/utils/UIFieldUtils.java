package org.homio.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.validation.MaxItems;
import org.homio.api.entity.validation.UIFieldValidationSize;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.WebAddress;
import org.homio.api.model.endpoint.DeviceEndpointUI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.UIFieldProgress.UIFieldProgressColorChange;
import org.homio.api.ui.field.action.*;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.color.*;
import org.homio.api.ui.field.condition.UIFieldDisableCreateTab;
import org.homio.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.image.UIFieldImage;
import org.homio.api.ui.field.image.UIFieldImageSrc;
import org.homio.api.ui.field.inline.*;
import org.homio.api.ui.field.selection.*;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.UIFieldClickToEdit;
import org.homio.app.model.entity.widget.*;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.model.var.UIFieldVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.*;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public class UIFieldUtils {

    public static @NotNull JSONObject buildDynamicParameterMetadata(@Nullable Object requestedEntity, @Nullable Class<?> sourceClassType) {
        JSONObject meta = new JSONObject();
        if (requestedEntity instanceof HasDynamicParameterFields && sourceClassType != null) {
            if (sourceClassType.equals(HasGetStatusValue.class)) {
                meta.put("get", true);
            } else if (sourceClassType.equals(HasSetStatusValue.class)) {
                meta.put("set", true);
            }
        }
        return meta;
    }

    public static Collection<UIInputEntity> fetchUIActionsFromClass(Class<?> clazz, Context context) {
        if (clazz != null) {
            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIContextMenuAction.class)) {
                UIContextMenuAction action = method.getDeclaredAnnotation(UIContextMenuAction.class);
                Icon icon = new Icon(action.icon(), action.iconColor());
                if (action.inputs().length > 0) {
                    ((UIInputBuilderImpl) uiInputBuilder)
                            .addOpenDialogSelectableButtonInternal(action.value(), icon, null)
                            .editDialog(dialogLayoutBuilder -> {
                                for (UIActionInput actionInput : action.inputs()) {
                                    ((UIDialogLayoutBuilderImpl) dialogLayoutBuilder).addInput(actionInput);
                                }
                            });
                } else {
                    uiInputBuilder.addSelectableButton(action.value(), icon, null)
                            .setConfirmMessage(action.confirmMessage())
                            .setConfirmMessageDialogColor(action.confirmMessageDialogColor())
                            .setText(action.value());
                }
            }
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIContextMenuUploadAction.class)) {
                UIContextMenuUploadAction action = method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
                Icon icon = new Icon(action.icon(), action.iconColor());
                uiInputBuilder.addSimpleUploadButton(action.value(), icon, action.supportedFormats(), null, 0);
            }
            return uiInputBuilder.buildAll();
        }
        return Collections.emptyList();
    }

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class<?> entityClassByType, Context context) {
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>(), context);
    }

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class<?> entityClassByType,
                                                                  Set<EntityUIMetaData> entityUIMetaDataSet,
                                                                  Context context) {
        if (entityClassByType == null) {
            return Collections.emptyList();
        }
        // try fetch instance from some UI Dialogs that registered via: addOpenDialogSelectableButtonFromClassInstance
        Object instance = ContextImpl.FIELD_FETCH_TYPE.get(entityClassByType.getSimpleName());
        if(instance == null) {
            instance = CommonUtils.newInstance(entityClassByType);
        }
        if (instance instanceof BaseEntity be) {
            be.setEntityID("FETCH_UI_FIELD_SPECIFICATIONS");
            be.setContext(context);
        }
        List<EntityUIMetaData> result = fillEntityUIMetadataList(instance, entityUIMetaDataSet, context, false, null);
        if (instance instanceof ConfigureFieldsService configurator) {
            configurator.configure(result);
        }
        return result;
    }

    public static List<EntityUIMetaData> fillEntityUIMetadataList(
            @NotNull Object instance,
            @NotNull Set<EntityUIMetaData> entityUIMetaDataSet,
            @NotNull Context context,
            boolean fullDisableEdit,
            @Nullable EntityUIMetaData entityUIMetaData) {

        if (instance instanceof DeviceEndpointUI) {
            // special case for device endpoints
            EntityUIMetaData deMetadata = new EntityUIMetaData().setEntityName("endpoints").setType("Endpoint");
            if (entityUIMetaData != null) {
                deMetadata.setEntityName(entityUIMetaData.getEntityName());
            }
            return List.of(deMetadata);
        }

        Map<String, List<Method>> fieldNameToGettersMap = new HashMap<>();

        List<Class<?>> classes = getAllSuperclassesAndInterfaces(instance.getClass());
        classes.add(0, instance.getClass());
        for (final Class<?> cursor : classes) {
            for (Method declaredMethod : cursor.getDeclaredMethods()) {
                fieldNameToGettersMap.putIfAbsent(declaredMethod.getName(), new ArrayList<>());
                fieldNameToGettersMap.get(declaredMethod.getName()).add(declaredMethod);
            }
        }

        // filter methods with @UIField
        Map<String, UIFieldMethodContext> fieldNameToGetters = new HashMap<>();
        for (Method fieldMethod : MethodUtils.getMethodsListWithAnnotation(instance.getClass(), UIField.class, true, false)) {
            if (!fieldNameToGetters.containsKey(fieldMethod.getName())) {
                fieldNameToGetters.put(fieldMethod.getName(),
                        new UIFieldMethodContext(fieldMethod, fieldNameToGettersMap.get(fieldMethod.getName())));
            }
        }

        for (UIFieldMethodContext fieldMethodContext : fieldNameToGetters.values()) {
            generateUIField(instance, entityUIMetaDataSet, fieldMethodContext, context, fullDisableEdit);
        }

        Set<String> processedMethods = fieldNameToGetters.values().stream().map(UIFieldMethodContext::getMethodName).collect(toSet());

        FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
            if (!processedMethods.contains(field.getName())) { // skip if already managed by methods
                String capitalizeMethodName = StringUtils.capitalize(field.getName());
                List<Method> fieldGetterMethods =
                        fieldNameToGettersMap.getOrDefault("get" + capitalizeMethodName, new ArrayList<>());
                fieldGetterMethods.addAll(fieldNameToGettersMap.getOrDefault("is" + capitalizeMethodName, Collections.emptyList()));
                generateUIField(instance, entityUIMetaDataSet, new UIFieldFieldContext(field, fieldGetterMethods), context, fullDisableEdit);
            }
        });

        return new ArrayList<>(entityUIMetaDataSet);
    }

    public static String getClassEntityNavLink(String name, Class<?> entityClass) {
        var route = CommonUtils.getClassRoute(entityClass);
        if (route == null) {
            throw new IllegalArgumentException("Unable to create link for field: " + name + " and class: " + entityClass.getSimpleName());
        }
        String href = StringUtils.defaultIfEmpty(route.path(), entityClass.getSimpleName());
        return route.parent().name().toLowerCase() + "/" + href;
    }

    @SneakyThrows
    private static void generateUIField(Object instance, Set<EntityUIMetaData> entityUIMetaDataList,
                                        UIFieldContext fieldContext, Context context, boolean fullDisableEdit) {
        EntityUIMetaData entityUIMetaData = new EntityUIMetaData();
        entityUIMetaData.setEntityName(fieldContext.getName());
        Type genericType = fieldContext.getGenericType();
        String sourceName = StringUtils.uncapitalize(fieldContext.getSourceName());
        Class<?> type = fieldContext.getType();
        UIField field = fieldContext.getUIField();

        if (fieldContext.isAnnotationPresent(UIFieldIgnore.class, true)) {
            entityUIMetaDataList.remove(entityUIMetaData);
            return; // skip transparent UIFields
        }

        entityUIMetaData.setLabel(trimToNull(field.label()));
        entityUIMetaData.setColor(trimToNull(field.color()));
        entityUIMetaData.setIcon(trimToNull(field.icon()));
        entityUIMetaData.setInlineEdit(nullIfFalse(field.inlineEdit()));
        entityUIMetaData.setCopyButton(nullIfFalse(field.copyButton()));
        entityUIMetaData.setInlineEditWhenEmpty(nullIfFalse(field.inlineEditWhenEmpty()));
        entityUIMetaData.setSemiRequired(nullIfFalse(field.semiRequired()));
        entityUIMetaData.setHideOnEmpty(nullIfFalse(field.hideOnEmpty()));
        entityUIMetaData.setValueSuffix(field.valueSuffix());
        entityUIMetaData.setValueSuffixColor(field.valueSuffixColor());

        entityUIMetaData.setHideInEdit(nullIfFalse(field.hideInEdit()));
        entityUIMetaData.setDisableEdit(nullIfFalse(field.disableEdit()));
        entityUIMetaData.setHideInView(nullIfFalse(field.hideInView()));

        entityUIMetaData.setStyle(field.style());

        // make sense keep defaultValue(for revert) only if able to edit value
        boolean noReadDefaultValue = fieldContext.isAnnotationPresent(UIFieldNoReadDefaultValue.class);
        if (!noReadDefaultValue
            && !fullDisableEdit && !field.disableEdit()
            && field.type() != UIFieldType.Duration
            && field.type() != UIFieldType.DurationDowntime
            && field.type() != UIFieldType.Password
            && field.type() != UIFieldType.HTML
            && field.type() != UIFieldType.Text
            && !type.isAssignableFrom(SecureString.class)) {
            try {
                entityUIMetaData.setDefaultValue(fieldContext.getDefaultValue(instance));
            } catch (Exception ex) {
                log.error("Unable to get default value for field: {}", fieldContext.getName(), ex);
            }
        }

        ObjectNode jsonTypeMetadata = OBJECT_MAPPER.createObjectNode();

        if (type.isEnum() && !noReadDefaultValue) {
            try {
                entityUIMetaData.setDefaultValue(fieldContext.getDefaultValue(instance));
            } catch (Exception ex) {
                log.error("Unable to get default value for field: {}", fieldContext.getName(), ex);
            }
        }

        detectFieldType(instance, fieldContext, entityUIMetaData, genericType, type, field, jsonTypeMetadata);

        // @UIField(order = 9999, disableEdit = true)
        // @UIFieldInlineEntities(bg = "#27FF000D")
        if (entityUIMetaData.getType() == null) {
            if (fieldContext.isAnnotationPresent(UIFieldInlineEntities.class)) {
                entityUIMetaData.setType("List");
            } else if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                Type typeArgument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                if (typeArgument instanceof Class) {
                    if (String.class.isAssignableFrom((Class<?>) typeArgument)) {
                        entityUIMetaData.setType(UIFieldType.Chips.name());
                    } else if (UIInputEntity.class.isAssignableFrom((Class<?>) typeArgument)) {
                        entityUIMetaData.setType("Actions");
                    }
                }
            }
        }

        putIfNonEmpty(jsonTypeMetadata, "helpLabel", field.descriptionLabel());
        putIfTrue(jsonTypeMetadata, "fw", field.fullWidth());
        putIfTrue(jsonTypeMetadata, "showLabelInFw", field.hideLabelInFullWidth());
        putIfNonEmpty(jsonTypeMetadata, "bg", field.bg());

        UIFieldPadding fieldPadding = fieldContext.getDeclaredAnnotation(UIFieldPadding.class);
        if (fieldPadding != null) {
            entityUIMetaData.setType("Padding");
            putIfNotEqual(jsonTypeMetadata, "min", fieldPadding.min(), 0);
            putIfNotEqual(jsonTypeMetadata, "max", fieldPadding.max(), 9);
        }

        UIFieldIconPicker fieldIconPicker = fieldContext.getDeclaredAnnotation(UIFieldIconPicker.class);
        if (fieldIconPicker != null) {
            entityUIMetaData.setType("IconPicker");
            putIfTrue(jsonTypeMetadata, "asObject", fieldIconPicker.asObject());
            putIfTrue(jsonTypeMetadata, "allowEmptyIcon", fieldIconPicker.allowEmptyIcon());
            putIfTrue(jsonTypeMetadata, "allowSize", fieldIconPicker.allowSize());
            putIfTrue(jsonTypeMetadata, "allowSpin", fieldIconPicker.allowSpin());
            putIfTrue(jsonTypeMetadata, "allowThreshold", fieldIconPicker.allowThreshold());
            putIfTrue(jsonTypeMetadata, "allowBackground", fieldIconPicker.allowBackground());
        }

        UIFieldVariable fieldVariable = fieldContext.getDeclaredAnnotation(UIFieldVariable.class);
        if (fieldVariable != null) {
            entityUIMetaData.setType("Variable");
        }

        UIFieldPosition fieldPosition = fieldContext.getDeclaredAnnotation(UIFieldPosition.class);
        if (fieldPosition != null) {
            entityUIMetaData.setType("Position");
            putIfTrue(jsonTypeMetadata, "disableCenter", fieldPosition.disableCenter());
        }

        UIFieldFunction fieldFunction = fieldContext.getDeclaredAnnotation(UIFieldFunction.class);
        if (fieldFunction != null) {
            jsonTypeMetadata.put("func", fieldFunction.value());
        }

        UIFieldColorPicker fieldColorPicker = fieldContext.getDeclaredAnnotation(UIFieldColorPicker.class);
        if (fieldColorPicker != null) {
            entityUIMetaData.setType(UIFieldType.ColorPicker.name());
            putIfTrue(jsonTypeMetadata, "allowThreshold", fieldColorPicker.allowThreshold());
            putIfTrue(jsonTypeMetadata, "pulseColorCondition", fieldColorPicker.pulseColorCondition());
            putIfTrue(jsonTypeMetadata, "thresholdSource", fieldColorPicker.thresholdSource());
        }

        UIFieldTableLayout fieldTableLayout = fieldContext.getDeclaredAnnotation(UIFieldTableLayout.class);
        if (fieldTableLayout != null) {
            entityUIMetaData.setType("TableLayout");
            jsonTypeMetadata.put("maxRows", fieldTableLayout.maxRows());
            jsonTypeMetadata.put("maxColumns", fieldTableLayout.maxColumns());
        }

        if (entityUIMetaData.getType() != null) {
            UIFieldSelectionUtil.handleFieldSelections(fieldContext, jsonTypeMetadata);
        }

        var fieldMarkers = fieldContext.getDeclaredAnnotation(UIFieldMarkers.class);
        if (fieldMarkers != null) {
            entityUIMetaData.setType("Markers");
            jsonTypeMetadata.set("markerOP", OBJECT_MAPPER.valueToTree(fieldMarkers.value()));
        }

        var fieldLayout = fieldContext.getDeclaredAnnotation(UIFieldLayout.class);
        if (fieldLayout != null) {
            entityUIMetaData.setType("Layout");
            jsonTypeMetadata.put("layoutRows", fieldLayout.rows());
            jsonTypeMetadata.put("layoutColumns", fieldLayout.columns());
            jsonTypeMetadata.set("layoutOptions", OBJECT_MAPPER.valueToTree(fieldLayout.options()));
        }

        var fieldShowOnCondition = fieldContext.getDeclaredAnnotation(UIFieldShowOnCondition.class);
        if (fieldShowOnCondition != null) {
            jsonTypeMetadata.put("showCondition", fieldShowOnCondition.value());
        }
        var disableEditOnCondition = fieldContext.getDeclaredAnnotation(UIFieldDisableEditOnCondition.class);
        if (disableEditOnCondition != null) {
            jsonTypeMetadata.put("disableEditCondition", disableEditOnCondition.value());
        }

        var fieldGroup = fieldContext.getDeclaredAnnotation(UIFieldGroup.class);
        if (fieldGroup != null) {
            jsonTypeMetadata.put("group", "GROUP." + fieldGroup.value());
            if (fieldGroup.order() > 0) {
                jsonTypeMetadata.put("groupOrder", fieldGroup.order());
            }
            putIfNonEmpty(jsonTypeMetadata, "borderColor", fieldGroup.borderColor());
        }

        putIfTrue(jsonTypeMetadata, "reloadOnUpdate", fieldContext.isAnnotationPresent(UIEditReloadWidget.class));

        Pattern pattern = fieldContext.getDeclaredAnnotation(Pattern.class);
        if (pattern != null) {
            jsonTypeMetadata.put("regexp", pattern.regexp());
        }

        var fieldTimeSlider = fieldContext.getDeclaredAnnotation(UIFieldTimeSlider.class);
        if (fieldTimeSlider != null) {
            entityUIMetaData.setType("TimeSlider");
        }

        var fieldProgress = fieldContext.getDeclaredAnnotation(UIFieldProgress.class);
        if (fieldProgress != null) {
            JsonNode colorChange = OBJECT_MAPPER.valueToTree(Arrays.stream(fieldProgress.colorChange()).collect(
                    Collectors.toMap(UIFieldProgressColorChange::color, UIFieldProgressColorChange::whenMoreThan)));
            entityUIMetaData.setType("Progress");
            jsonTypeMetadata.put("pgColor", fieldProgress.color());
            jsonTypeMetadata.set("colorChange", colorChange);
        }

        var fieldImage = fieldContext.getDeclaredAnnotation(UIFieldImage.class);
        if (fieldImage != null) {
            entityUIMetaData.setType("Image");
            jsonTypeMetadata.put("maxWidth", fieldImage.maxWidth());
            jsonTypeMetadata.put("maxHeight", fieldImage.maxHeight());
        }

        var fieldImageSrc = fieldContext.getDeclaredAnnotation(UIFieldImageSrc.class);
        if (fieldImageSrc != null) {
            entityUIMetaData.setType("ImageSrc");
            jsonTypeMetadata.put("maxWidth", fieldImageSrc.maxWidth());
            jsonTypeMetadata.put("maxHeight", fieldImageSrc.maxHeight());
        }

        List<UIActionButton> uiActionButtons = fieldContext.getDeclaredAnnotationsByType(UIActionButton.class);
        if (!uiActionButtons.isEmpty()) {
            ArrayNode actionButtons = OBJECT_MAPPER.createArrayNode();
            for (UIActionButton actionButton : uiActionButtons) {
                ArrayNode inputs = OBJECT_MAPPER.createArrayNode();
                for (UIActionInput actionInput : actionButton.inputs()) {
                    inputs.add(OBJECT_MAPPER.valueToTree(new ActionInputParameter(actionInput)));
                }
                actionButtons.add(OBJECT_MAPPER.createObjectNode()
                        .put("name", actionButton.name())
                        .put("icon", actionButton.icon())
                        .put("color", actionButton.color())
                        .putPOJO("inputs", inputs)
                        .put("style", actionButton.style()));
            }
            jsonTypeMetadata.set("actionButtons", actionButtons);
        }

        var fieldPort = fieldContext.getDeclaredAnnotation(UIFieldPort.class);
        if (fieldPort != null) {
            jsonTypeMetadata.put("min", fieldPort.min());
            jsonTypeMetadata.put("max", fieldPort.max());
            entityUIMetaData.setType("Port");
        }

        List<UIFieldColorMatch> fieldColorMatches = fieldContext.getDeclaredAnnotationsByType(UIFieldColorMatch.class);
        if (!fieldColorMatches.isEmpty()) {
            JSONObject colors = new JSONObject();
            for (UIFieldColorMatch fieldColorMatch : fieldColorMatches) {
                colors.put(fieldColorMatch.value(), fieldColorMatch.color());
            }
            jsonTypeMetadata.set("valueColor", OBJECT_MAPPER.valueToTree(colors));
        }

        var fieldColorStatusMatch = fieldContext.getDeclaredAnnotation(UIFieldColorStatusMatch.class);
        if (fieldColorStatusMatch != null) {
            ObjectNode colors = OBJECT_MAPPER.createObjectNode();
            colors.put(Status.OFFLINE.name(), fieldColorStatusMatch.offline());
            colors.put(Status.INITIALIZE.name(), fieldColorStatusMatch.init());
            colors.put(Status.ONLINE.name(), fieldColorStatusMatch.online());
            colors.put(Status.UNKNOWN.name(), fieldColorStatusMatch.unknown());
            colors.put(Status.ERROR.name(), fieldColorStatusMatch.error());
            colors.put(Status.REQUIRE_AUTH.name(), fieldColorStatusMatch.requireAuth());
            colors.put(Status.DONE.name(), fieldColorStatusMatch.done());
            colors.put(Status.NOT_SUPPORTED.name(), fieldColorStatusMatch.notSupported());
            colors.put(Status.RUNNING.name(), fieldColorStatusMatch.running());
            colors.put(Status.WAITING.name(), fieldColorStatusMatch.waiting());
            colors.put(Status.INITIALIZE.name(), fieldColorStatusMatch.init());
            colors.put(Status.CLOSING.name(), fieldColorStatusMatch.closing());
            colors.put(Status.RESTARTING.name(), fieldColorStatusMatch.restarting());
            colors.put(Status.NOT_READY.name(), fieldColorStatusMatch.notReady());
            jsonTypeMetadata.set("valueColor", colors);
            jsonTypeMetadata.put("valueColorPrefix", fieldColorStatusMatch.handlePrefixes());
        }

        var fieldColorBooleanMatch = fieldContext.getDeclaredAnnotation(UIFieldColorBooleanMatch.class);
        if (fieldColorBooleanMatch != null) {
            JSONObject colors = new JSONObject();
            colors.put("true", fieldColorBooleanMatch.False());
            colors.put("false", fieldColorBooleanMatch.True());
            jsonTypeMetadata.set("valueColor", OBJECT_MAPPER.valueToTree(colors));
        }

        var fieldColorRef = fieldContext.getDeclaredAnnotation(UIFieldColorRef.class);
        if (fieldColorRef != null) {
            assertFieldExists(instance, fieldColorRef.value());
            jsonTypeMetadata.put("colorRef", fieldColorRef.value());
        }

        var fieldColorBgRef = fieldContext.getDeclaredAnnotation(UIFieldColorBgRef.class);
        if (fieldColorBgRef != null) {
            assertFieldExists(instance, fieldColorBgRef.value());
            jsonTypeMetadata.put("colorBgRef", fieldColorBgRef.value());
            putIfTrue(jsonTypeMetadata, "colorBgRefAnimate", fieldColorBgRef.animate());
        }

        var fieldInlineEditConfirm = fieldContext.getDeclaredAnnotation(UIFieldInlineEditConfirm.class);
        if (fieldInlineEditConfirm != null) {
            if (!field.inlineEdit()) {
                throw new IllegalArgumentException("Annotate @UIFieldInlineEditConfirm without inlineEdit() is prohibited. " + fieldContext);
            }
            jsonTypeMetadata.put("iec", "W.CONFIRM." + fieldInlineEditConfirm.value());
            putIfNonEmpty(jsonTypeMetadata, "iec_color", fieldInlineEditConfirm.dialogColor());
            putIfNonEmpty(jsonTypeMetadata, "iec_condition", fieldInlineEditConfirm.showCondition());
        }

        var fieldTab = fieldContext.getDeclaredAnnotation(UIFieldTab.class);
        if (fieldTab != null) {
            jsonTypeMetadata.put("tab", fieldTab.value());
            if (fieldTab.order() > 1) {
                jsonTypeMetadata.put("tabOrder", fieldTab.order());
            }
            putIfNonEmpty(jsonTypeMetadata, "tabColor", fieldTab.color());
        }

        var fieldLink = fieldContext.getDeclaredAnnotation(UIFieldLinkToRoute.class);
        if (fieldLink != null) {
            String link = fieldLink.rawRoute() == null ? getClassEntityNavLink(field.name(), fieldLink.value()) : fieldLink.rawRoute();
            entityUIMetaData.setNavLink(link);
        }

        UIFieldSelectConfig selectConfig = fieldContext.getDeclaredAnnotation(UIFieldSelectConfig.class);
        if (selectConfig != null) {
            ObjectNode select = getSelectConfig(jsonTypeMetadata);
            select.put("addEmptySelection", selectConfig.addEmptySelection());
            select.putPOJO("icon", new Icon(selectConfig.icon(), selectConfig.iconColor()));
            if (isNotEmpty(selectConfig.selectOnEmptyLabel())) {
                ObjectNode selectValueOnEmpty = OBJECT_MAPPER.createObjectNode();
                selectValueOnEmpty.put("label", selectConfig.selectOnEmptyLabel());
                selectValueOnEmpty.putPOJO("icon", new Icon(selectConfig.selectOnEmptyIcon(), selectConfig.selectOnEmptyColor()));
                select.set("selectValueOnEmpty", selectValueOnEmpty);
            }
            putIfNonEmpty(select, "optionsNotFound", selectConfig.selectNoValue());
        }

        if (String.class.getSimpleName().equals(entityUIMetaData.getType())) {
            UIFieldKeyValue uiKeyValueField = fieldContext.getDeclaredAnnotation(UIFieldKeyValue.class);
            if (uiKeyValueField != null) {
                jsonTypeMetadata.put("maxSize", uiKeyValueField.maxSize());
                jsonTypeMetadata.set("keyType", OBJECT_MAPPER.valueToTree(uiKeyValueField.keyType()));
                jsonTypeMetadata.set("valueType", OBJECT_MAPPER.valueToTree(uiKeyValueField.valueType()));
                putIfNonEmpty(jsonTypeMetadata, "defaultKey", uiKeyValueField.defaultKey());
                putIfNonEmpty(jsonTypeMetadata, "defaultValue", uiKeyValueField.defaultValue());
                jsonTypeMetadata.put("keyFormat", uiKeyValueField.keyFormat());
                jsonTypeMetadata.put("valueFormat", uiKeyValueField.valueFormat());
                jsonTypeMetadata.put("keyValueType", uiKeyValueField.keyValueType().name());

                putIfNonEmpty(jsonTypeMetadata, "keyPlaceholder", uiKeyValueField.keyPlaceholder());
                putIfNonEmpty(jsonTypeMetadata, "valuePlaceholder", uiKeyValueField.valuePlaceholder());

                putIfTrue(jsonTypeMetadata, "showKey", uiKeyValueField.showKey());

                /*ArrayNode options = OBJECT_MAPPER.createArrayNode();
                for (Option option : uiKeyValueField.options()) {
                    options.add(OBJECT_MAPPER.createObjectNode().put("key", option.key()).putPOJO("values", option.values()));
                }
                if (!options.isEmpty()) {
                    jsonTypeMetadata.putPOJO("keyValueOptions", options);
                }*/

                entityUIMetaData.setType("KeyValue");
            }
        }

        assembleTextOptions(fieldContext, sourceName, jsonTypeMetadata);

        var fieldNumber = fieldContext.getDeclaredAnnotation(UIFieldNumber.class);
        if (fieldNumber != null) {
            jsonTypeMetadata.put("min", fieldNumber.min());
            jsonTypeMetadata.put("max", fieldNumber.max());
        }

        var fieldTemplate = fieldContext.getDeclaredAnnotation(UIFieldStringTemplate.class);
        if (fieldTemplate != null) {
            jsonTypeMetadata.putPOJO("template", toRecord(fieldTemplate));
            entityUIMetaData.setType("StringTemplate");
        }

        var fieldSlider = fieldContext.getDeclaredAnnotation(UIFieldSlider.class);
        if (fieldSlider != null) {
            jsonTypeMetadata.put("min", fieldSlider.min());
            jsonTypeMetadata.put("max", fieldSlider.max());
            jsonTypeMetadata.put("step", fieldSlider.step());
            entityUIMetaData.setType(UIFieldType.Slider.name());
            entityUIMetaData.setDefaultValue(fieldContext.getDefaultValue(instance));
        }

        var fieldCodeEditor = fieldContext.getDeclaredAnnotation(UIFieldCodeEditor.class);
        if (fieldCodeEditor != null) {
            jsonTypeMetadata.put("wordWrap", fieldCodeEditor.wordWrap());
            jsonTypeMetadata.put("autoFormat", fieldCodeEditor.autoFormat());
            jsonTypeMetadata.put("editorType", fieldCodeEditor.editorType().name());
            String editorTypeRef = fieldCodeEditor.editorTypeRef();
            if (isNotEmpty(editorTypeRef)) {
                assertFieldExists(instance, editorTypeRef);
            }
            jsonTypeMetadata.put("editorTypeRef", editorTypeRef);
            entityUIMetaData.setType("CodeEditor");
        }

        UIFieldValidationSize fieldValidationSize = fieldContext.getDeclaredAnnotation(UIFieldValidationSize.class);
        if (fieldValidationSize != null) {
            jsonTypeMetadata.put("min", fieldValidationSize.min());
            jsonTypeMetadata.put("max", fieldValidationSize.max());
        }

        putIfTrue(jsonTypeMetadata, "disableCreateTab", fieldContext.isAnnotationPresent(UIFieldDisableCreateTab.class));

        var fieldInlineGroup = fieldContext.getDeclaredAnnotation(UIFieldInlineGroup.class);
        if (fieldInlineGroup != null) {
            jsonTypeMetadata.put("inlineShowGroupCondition", fieldInlineGroup.value());
            putIfTrue(jsonTypeMetadata, "inlineGroupEditable", fieldInlineGroup.editable());
        }
        var fieldInlineEntityEditWidth =
                fieldContext.getDeclaredAnnotation(UIFieldInlineEntityEditWidth.class);
        if (fieldInlineEntityEditWidth != null && fieldInlineEntityEditWidth.value() >= 0) {
            jsonTypeMetadata.put("inlineEditWidth", fieldInlineEntityEditWidth.value());
        }
        var fieldInlineEntityWidth = fieldContext.getDeclaredAnnotation(UIFieldInlineEntityWidth.class);
        if (fieldInlineEntityWidth != null && fieldInlineEntityWidth.value() >= 0) {
            jsonTypeMetadata.put("inlineViewWidth", fieldInlineEntityWidth.value());
        }

        var fieldClickToEdit = fieldContext.getDeclaredAnnotation(UIFieldClickToEdit.class);
        if (fieldClickToEdit != null) {
            assertFieldExists(instance, fieldClickToEdit.value());
            jsonTypeMetadata.put("clickToEdit", fieldClickToEdit.value());
        }

        var fieldInlineEdit = fieldContext.getDeclaredAnnotation(UIFieldInlineEditEntities.class);
        if (fieldInlineEdit != null) {
            if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                putUIInlineFieldIfRequire(instance, fieldContext, entityUIMetaData, (ParameterizedType) genericType, jsonTypeMetadata, context);
                entityUIMetaData.setStyle("padding: 0; background: " + fieldInlineEdit.bg() + ";");

                jsonTypeMetadata.put("fw", true);
                jsonTypeMetadata.put("addRow", fieldInlineEdit.addRowLabel());
                jsonTypeMetadata.put("addRowCondition", fieldInlineEdit.addRowCondition());
                jsonTypeMetadata.put("delRowCondition", fieldInlineEdit.removeRowCondition());
                jsonTypeMetadata.put("noContentTitle", fieldInlineEdit.noContentTitle());
                jsonTypeMetadata.put("showInGeneralEdit", true);
            } else {
                throw new IllegalStateException("Unable to annotate field " + fieldContext.getSourceName() + " with @UIFieldInlineEditEntities");
            }
        }

        var fieldInline = fieldContext.getDeclaredAnnotation(UIFieldInlineEntities.class);
        if (fieldInline != null) {
            if (genericType instanceof ParameterizedType
                && Collection.class.isAssignableFrom(type)) {
                putUIInlineFieldIfRequire(
                        instance,
                        fieldContext,
                        entityUIMetaData,
                        (ParameterizedType) genericType,
                        jsonTypeMetadata,
                        context);

                entityUIMetaData.setStyle("padding: 0; background: " + fieldInline.bg() + ";");
                jsonTypeMetadata.put("fw", true);
                jsonTypeMetadata.put("noContentTitle", fieldInline.noContentTitle());
                jsonTypeMetadata.put("showInGeneral", true);
            } else {
                throw new IllegalStateException(
                        "Unable to annotate field "
                        + fieldContext.getSourceName()
                        + " with @UIFieldInlineEntities");
            }
        }

        entityUIMetaData.setShowInContextMenu(nullIfFalse(field.showInContextMenu() && entityUIMetaData.getType().equals(Boolean.class.getSimpleName())));

        if (!jsonTypeMetadata.isEmpty()) {
            entityUIMetaData.setTypeMetaData(jsonTypeMetadata.toString());
        }
        entityUIMetaData.setOrder(field.order());

        entityUIMetaData.setRequired(nullIfFalse(field.required()));
        var route = CommonUtils.getClassRoute(type);
        if (BaseEntity.class.isAssignableFrom(type) && route != null) {
            entityUIMetaData.setNavLink(getClassEntityNavLink(field.name(), type));
        }
        if (entityUIMetaData.getType() == null) {
            throw new RuntimeException("Unable to evaluate field '" + sourceName + "' type for class: " + instance.getClass().getSimpleName());
        }

        entityUIMetaDataList.remove(entityUIMetaData);
        entityUIMetaDataList.add(entityUIMetaData);
    }

    private static void putIfNotEqual(ObjectNode metadata, String key, int value, int eqValue) {
        if (value != eqValue) {
            metadata.put(key, value);
        }
    }

    private static ObjectNode getSelectConfig(ObjectNode jsonTypeMetadata) {
        if (!jsonTypeMetadata.has("selectConfig")) {
            ObjectNode select = OBJECT_MAPPER.createObjectNode();
            jsonTypeMetadata.set("selectConfig", select);
        }
        return (ObjectNode) jsonTypeMetadata.get("selectConfig");
    }

    private static void detectFieldType(Object instance, UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData, Type genericType, Class<?> type,
                                        UIField field, ObjectNode jsonTypeMetadata) {
        if (field.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum() || getSelectAnnotationCount(fieldContext) > 0) {
                detectFieldSelectionType(fieldContext, entityUIMetaData, type, field, jsonTypeMetadata);
            } else {
                if (genericType instanceof ParameterizedType
                    && Collection.class.isAssignableFrom(type)) {
                    extractSetEntityType(instance, fieldContext, entityUIMetaData, (ParameterizedType) genericType, jsonTypeMetadata);
                } else {
                    if (type.equals(UIFieldProgress.Progress.class)) {
                        entityUIMetaData.setType("Progress");
                    } else if (type.equals(boolean.class)) {
                        entityUIMetaData.setType(UIFieldType.Boolean.name());
                    } else if (type.equals(Date.class)) {
                        entityUIMetaData.setType(UIFieldType.StaticDate.name());
                    } else if (type.equals(float.class)) {
                        entityUIMetaData.setType(UIFieldType.Float.name());
                    } else if (type.equals(int.class)) {
                        entityUIMetaData.setType(UIFieldType.Integer.name());
                    } else if (type.equals(SecureString.class)) {
                        entityUIMetaData.setType(UIFieldType.String.name());
                    } else if (UIInputEntity.class.isAssignableFrom(type)) {
                        entityUIMetaData.setType("InputEntity");
                    } else if (type.equals(WebAddress.class)) {
                        entityUIMetaData.setType(UIFieldType.HTML.name());
                    } else {
                        entityUIMetaData.setType(type.getSimpleName());
                    }
                }
            }
        } else {
            entityUIMetaData.setType(field.type().name());
        }
    }

    private static void detectFieldSelectionType(UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData, Class<?> type, UIField field,
                                                 ObjectNode jsonTypeMetadata) {
        // detect types

        UIFieldType fieldType = field.disableEdit() ? UIFieldType.String : UIFieldType.SelectBox;
        if (fieldContext.isAnnotationPresent(UIFieldDevicePortSelection.class)
            || isMatch(fieldContext, UIFieldEntityByClassSelection.class, UIFieldEntityByClassSelection::rawInput)
            || isMatch(fieldContext, UIFieldStaticSelection.class, UIFieldStaticSelection::rawInput)
            || isMatch(fieldContext, UIFieldTreeNodeSelection.class, UIFieldTreeNodeSelection::rawInput)
            || isMatch(fieldContext, UIFieldVariableSelection.class, UIFieldVariableSelection::rawInput)
            || isMatch(fieldContext, UIFieldDynamicSelection.class, UIFieldDynamicSelection::rawInput)
            || getSelectAnnotationCount(fieldContext) > 1) {
            getSelectConfig(jsonTypeMetadata).put("rawInput", true);
        }

        entityUIMetaData.setType(fieldType.name());
        if (Collection.class.isAssignableFrom(type)) {
            jsonTypeMetadata.put("multiple", true);
        }
    }

    private static <T extends Annotation> boolean isMatch(UIFieldContext fieldContext, Class<T> annotationClass, Predicate<T> predicate) {
        return fieldContext.isAnnotationPresent(annotationClass) && predicate.test(fieldContext.getDeclaredAnnotation(annotationClass));
    }

    private static void assembleTextOptions(UIFieldContext fieldContext, String sourceName, ObjectNode jsonTypeMetadata) {
        var fieldUpdateFontSize = fieldContext.getDeclaredAnnotation(UIFieldOptionFontSize.class);
        if (fieldUpdateFontSize != null) {
            jsonTypeMetadata.put("fsMin", fieldUpdateFontSize.min());
            jsonTypeMetadata.put("fsMax", fieldUpdateFontSize.max());
            jsonTypeMetadata.put("opt_fs", defaultIfEmpty(fieldUpdateFontSize.value(), sourceName));
        }

        var fieldVerticalAlign = fieldContext.getDeclaredAnnotation(UIFieldOptionVerticalAlign.class);
        if (fieldVerticalAlign != null) {
            jsonTypeMetadata.put("opt_va", defaultIfEmpty(fieldVerticalAlign.value(), sourceName));
        }

        var optionColor = fieldContext.getDeclaredAnnotation(UIFieldOptionColor.class);
        if (fieldVerticalAlign != null) {
            jsonTypeMetadata.put("opt_c", defaultIfEmpty(optionColor.value(), sourceName));
        }
    }

    public static void putIfNonEmpty(ObjectNode metadata, String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            metadata.put(key, value);
        }
    }

    private static void putIfTrue(ObjectNode metadata, String key, boolean value) {
        if (value) {
            metadata.put(key, true);
        }
    }

    private static void assertFieldExists(Object instance, String fieldName) {
        try {
            instance.getClass().getDeclaredField(fieldName);
        } catch (NoSuchFieldException ex) {
            if (CommonUtils.findMethodByName(instance.getClass(), fieldName) == null) {
                throw new ServerException("Unable to find field <" + fieldName + ">");
            }
        }
    }

    private static void putUIInlineFieldIfRequire(Object instance, UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData,
                                                  ParameterizedType genericType, ObjectNode jsonTypeMetadata, Context context) {
        if (!jsonTypeMetadata.has("inlineTypeFields")) {
            Type inlineType = extractSetEntityType(instance, fieldContext, entityUIMetaData, genericType, jsonTypeMetadata);
            // for non BaseEntity types
            if (inlineType == null) {
                inlineType = genericType.getActualTypeArguments()[0];
            }
            Object childClassInstance = CommonUtils.newInstance((Class) inlineType);
            boolean fullDisableEdit = fieldContext.getUIField().disableEdit();
            JsonNode inlineTypeFields = OBJECT_MAPPER.valueToTree(
                    UIFieldUtils.fillEntityUIMetadataList(childClassInstance, new HashSet<>(), context, fullDisableEdit, entityUIMetaData));
            if (!inlineTypeFields.isEmpty()) {
                jsonTypeMetadata.set("inlineTypeFields", inlineTypeFields);
                Collection<UIInputEntity> actions = fetchUIActionsFromClass((Class<?>) inlineType, context);
                if (actions != null && !actions.isEmpty()) {
                    jsonTypeMetadata.set("inlineTypeActions", OBJECT_MAPPER.valueToTree(actions));
                }
            }
        }
    }

    public static Boolean nullIfFalse(boolean value) {
        return value ? true : null;
    }

    // mosly for series
    private static Type extractSetEntityType(Object instance, UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData,
                                             ParameterizedType genericType, ObjectNode jsonTypeMetadata) {
        Type typeArgument = genericType.getActualTypeArguments()[0];
        if (!trySetListType(typeArgument, entityUIMetaData, fieldContext, jsonTypeMetadata, instance)) {
            typeArgument = findClassGenericClass(typeArgument, instance);
            if (typeArgument != null && !trySetListType(typeArgument, entityUIMetaData, fieldContext, jsonTypeMetadata, instance)) {
                entityUIMetaData.setType(UIFieldType.Chips.name());
                jsonTypeMetadata.put("type", ((Class) typeArgument).getSimpleName());
            } else {
                typeArgument = null;
            }
        }
        // very custom case Set<EnumValue> getEnumMultiList()
        var ta = genericType.getActualTypeArguments()[0];
        if (ta instanceof Class ac && ac.isEnum()) {
            entityUIMetaData.setType(UIFieldType.SelectBox.name());
            jsonTypeMetadata.put("type", ((Class<?>) ta).getSimpleName());
            jsonTypeMetadata.put("multiSelect", true);
            jsonTypeMetadata.put("actualEnum", ac.getName());
        }
        return typeArgument;
    }

    private static boolean trySetListType(Type argument, EntityUIMetaData entityUIMetaData, UIFieldContext fieldContext, ObjectNode jsonTypeMetadata, Object instance) {
        if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
            entityUIMetaData.setType("List");
            if (fieldContext.isAnnotationPresent(MaxItems.class)) {
                jsonTypeMetadata.put("max", fieldContext.getDeclaredAnnotation(MaxItems.class).value());
            }
            jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
            OneToMany oneToMany = fieldContext.getDeclaredAnnotation(OneToMany.class);
            if (oneToMany != null) {
                jsonTypeMetadata.put("mappedBy", oneToMany.mappedBy());
            }/* else if(instance instanceof HasSeriesEntities && fieldContext.getName().equals("series")) {
                jsonTypeMetadata.put("mappedBy", "in-memory-series");
            }*/
            return true;
        }
        return false;
    }

    private static Type findClassGenericClass(Type argument, Object instance) {
        if (argument instanceof TypeVariable<?> tv) {
            Class<?> contextClass = instance.getClass();
            Type resolved = resolveTypeVariable(contextClass, tv);
            return resolved != null ? resolved : tv;
        }
        return null;
    }

    public static Type resolveTypeVariable(Class<?> startingClass, TypeVariable<?> typeVariable) {
        GenericDeclaration declaringElement = typeVariable.getGenericDeclaration(); // Class/Interface that declared the TypeVariable (e.g., HasSeriesEntities.class)

        if (!(declaringElement instanceof Class)) {
            return null; // TypeVariable not declared by a class/interface (e.g., by a method - rare for this context)
        }
        Class<?> declaringClass = (Class<?>) declaringElement;

        Queue<TypeContext> queue = new LinkedList<>();
        queue.add(new TypeContext(startingClass, null)); // Start with the concrete class, no initial type map

        Set<Class<?>> visited = new HashSet<>(); // To avoid cycles and redundant processing of raw classes

        while (!queue.isEmpty()) {
            TypeContext current = queue.poll();
            Type currentType = current.type;

            Class<?> rawClass;
            ParameterizedType parameterizedCurrentType = null;

            if (currentType instanceof ParameterizedType) {
                parameterizedCurrentType = (ParameterizedType) currentType;
                rawClass = (Class<?>) parameterizedCurrentType.getRawType();
            } else if (currentType instanceof Class) {
                rawClass = (Class<?>) currentType;
            } else {
                continue; // Should not happen with proper queue additions
            }

            if (!visited.add(rawClass)) {
                continue;
            }

            // Build the current type map for rawClass using its actual arguments if it's parameterized
            // and mapping them from the parent context if needed.
            var currentTypeMap = new java.util.HashMap<TypeVariable<?>, Type>();
            if (parameterizedCurrentType != null) {
                TypeVariable<?>[] params = rawClass.getTypeParameters();
                Type[] actualArgs = parameterizedCurrentType.getActualTypeArguments();
                for (int i = 0; i < params.length; i++) {
                    Type actualArg = actualArgs[i];
                    // If the actual argument is itself a TypeVariable from the parent context, resolve it.
                    if (actualArg instanceof TypeVariable && current.typeMap != null) {
                        actualArg = current.typeMap.getOrDefault(actualArg, actualArg);
                    }
                    currentTypeMap.put(params[i], actualArg);
                }
            }
            // Merge with parent map
            if (current.typeMap != null) {
                current.typeMap.forEach(currentTypeMap::putIfAbsent);
            }


            // Is rawClass the class that declared our target typeVariable?
            if (declaringClass.equals(rawClass)) {
                Type resolvedType = currentTypeMap.get(typeVariable);
                if (resolvedType != null && !(resolvedType instanceof TypeVariable)) { // Ensure it's not an unresolved TV
                    return resolvedType;
                }
                // If it's still a TypeVariable, it means it was mapped from a higher level generic,
                // and we need to continue search or it's an error in setup.
                // For this specific problem, if HasSeriesEntities<T> -> HasSeriesEntities<HomekitEndpointEntity>,
                // resolvedType should be HomekitEndpointEntity.class
                // If resolvedType is null here, it means typeVariable was not in the type parameters of rawClass somehow (should not happen)
                // or it was not in the currentTypeMap, meaning it wasn't provided as an actual argument.
                if (parameterizedCurrentType != null) { // Check direct actual arguments
                    TypeVariable<?>[] params = declaringClass.getTypeParameters();
                    for (int i = 0; i < params.length; ++i) {
                        if (params[i].equals(typeVariable)) {
                            Type directActualArg = parameterizedCurrentType.getActualTypeArguments()[i];
                            if (directActualArg instanceof TypeVariable && current.typeMap != null) {
                                return current.typeMap.getOrDefault(directActualArg, directActualArg);
                            }
                            return directActualArg;
                        }
                    }
                }
            }

            // Add superclass to queue
            Type genericSuperclass = rawClass.getGenericSuperclass();
            if (genericSuperclass != null) {
                queue.add(new TypeContext(genericSuperclass, currentTypeMap));
            }

            // Add interfaces to queue
            for (Type genericInterface : rawClass.getGenericInterfaces()) {
                queue.add(new TypeContext(genericInterface, currentTypeMap));
            }
        }
        return null; // TypeVariable not resolved
    }

    private static <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass, List<Method> methods) {
        List<A> result = new ArrayList<>();
        for (Method method : methods) {
            result.addAll(Arrays.asList(method.getDeclaredAnnotationsByType(annotationClass)));
        }
        return result;
    }

    private static <A extends Annotation> A getDeclaredAnnotationFromMethods(Class<A> annotationClass, List<Method> methods) {
        for (Method method : methods) {
            if (method.isAnnotationPresent(annotationClass)) {
                return method.getDeclaredAnnotation(annotationClass);
            }
        }
        return null;
    }

    /**
     * Fully copied from MethodUtils.java commons-lang3 because it's private static
     */
    private static List<Class<?>> getAllSuperclassesAndInterfaces(final Class<?> cls) {
        if (cls == null) {
            return null;
        }

        final List<Class<?>> allSuperClassesAndInterfaces = new ArrayList<>();
        final List<Class<?>> allSuperclasses = ClassUtils.getAllSuperclasses(cls);
        int superClassIndex = 0;
        final List<Class<?>> allInterfaces = ClassUtils.getAllInterfaces(cls);
        int interfaceIndex = 0;
        while (interfaceIndex < allInterfaces.size() || superClassIndex < allSuperclasses.size()) {
            final Class<?> acls;
            if (interfaceIndex >= allInterfaces.size()) {
                acls = allSuperclasses.get(superClassIndex++);
            } else if ((superClassIndex >= allSuperclasses.size()) || (interfaceIndex < superClassIndex) ||
                       !(superClassIndex < interfaceIndex)) {
                acls = allInterfaces.get(interfaceIndex++);
            } else {
                acls = allSuperclasses.get(superClassIndex++);
            }
            allSuperClassesAndInterfaces.add(acls);
        }
        return allSuperClassesAndInterfaces;
    }

    private static int getSelectAnnotationCount(UIFieldContext fieldContext) {
        return fieldContext.getDeclaredAnnotationsByType(UIFieldTreeNodeSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldVariableSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldDynamicSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldDevicePortSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldStaticSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldEntityByClassSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldEntityTypeSelection.class).size()
               + fieldContext.getDeclaredAnnotationsByType(UIFieldBeanSelection.class).size();
    }

    private static Object toRecord(UIFieldStringTemplate annotation) {
        return new UIFieldStringTemplateRecord(
                annotation.allowSuffix(),
                annotation.allowPrefix(),
                annotation.allowValueClickShowHistoryOption()
        );
    }

    public interface UIFieldContext {

        String getName();

        Class<?> getType();

        UIField getUIField();

        Type getGenericType();

        String getSourceName();

        default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return !getDeclaredAnnotationsByType(annotationClass).isEmpty();
        }

        default boolean isAnnotationPresent(
                Class<? extends Annotation> annotationClass, boolean isOnlyFirstOccurrence) {
            return getDeclaredAnnotation(annotationClass, isOnlyFirstOccurrence) != null;
        }

        Object getDefaultValue(Object instance);

        default <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
            return getDeclaredAnnotation(annotationClass, false);
        }

        <A extends Annotation> A getDeclaredAnnotation(
                Class<A> annotationClass, boolean isOnlyFirstOccurrence);

        <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass);
    }

    public interface ConfigureFieldsService {

        void configure(@NotNull List<EntityUIMetaData> result);
    }

    private record TypeContext(Type type, java.util.Map<TypeVariable<?>, Type> typeMap) {
    }

    @RequiredArgsConstructor
    public static class UIFieldFieldContext implements UIFieldContext {

        private final Field field;
        private final List<Method> fieldGetterMethods;

        @Override
        public String getName() {
            return StringUtils.defaultIfEmpty(
                    field.getAnnotation(UIField.class).name(), field.getName());
        }

        @Override
        public Class<?> getType() {
            return field.getType();
        }

        @Override
        public UIField getUIField() {
            return field.getAnnotation(UIField.class);
        }

        @Override
        public Type getGenericType() {
            return field.getGenericType();
        }

        @Override
        public String getSourceName() {
            return field.getName();
        }

        @Override
        @SneakyThrows
        public Object getDefaultValue(Object instance) {
            return fieldGetterMethods.isEmpty() ? FieldUtils.readField(field, instance, true) :
                    fieldGetterMethods.get(0).invoke(instance);
        }

        @Override
        public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass, boolean isOnlyFirstOccurrence) {
            A annotation = UIFieldUtils.getDeclaredAnnotationFromMethods(annotationClass, fieldGetterMethods);
            return annotation == null ? field.getDeclaredAnnotation(annotationClass) : annotation;
        }

        @Override
        public <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass) {
            List<A> result = UIFieldUtils.getDeclaredAnnotationsByType(annotationClass, fieldGetterMethods);
            result.addAll(Arrays.asList(field.getDeclaredAnnotationsByType(annotationClass)));
            return result;
        }

        @Override
        public String toString() {
            return "UIFieldFieldContext{name=%stype=%s}".formatted(getName(), getType());
        }
    }

    public static class UIFieldMethodContext implements UIFieldContext {

        private final String name;
        @Getter
        private final String methodName;
        private final List<Method> methods = new ArrayList<>();

        public UIFieldMethodContext(Method fieldMethod, List<Method> allMethods) {
            for (Method method : allMethods) {
                // add only methods with zero argument count
                if (method.getParameterCount() == 0) {
                    methods.add(method);
                }
                // when we see @UIFieldIgnoreParent than ignore super annotations
                if (method.isAnnotationPresent(UIFieldIgnoreParent.class)) {
                    break;
                }
            }
            UIField uiField = getDeclaredAnnotation(UIField.class);
            if (uiField == null) {
                String message = "Unable to fetch @UIField annotations from method: " + fieldMethod.getName()
                                 + " of class: " + fieldMethod.getDeclaringClass().getSimpleName();
                log.error(message);
                throw new IllegalStateException(message);
            }
            this.name = uiField.name();
            this.methodName = getMethodShortName(fieldMethod);
        }

        private static String getMethodShortName(Method method) {
            return StringUtils.uncapitalize(method.getName().substring(method.getName().startsWith("is") ? 2 : 3));
        }

        @Override
        public String getName() {
            return StringUtils.defaultIfEmpty(name, methodName);
        }

        @Override
        public Class<?> getType() {
            return methods.get(0).getReturnType();
        }

        @Override
        public UIField getUIField() {
            return methods.stream().filter(m -> m.isAnnotationPresent(UIField.class))
                    .findFirst().map(s -> s.getDeclaredAnnotation(UIField.class)).orElse(null);
        }

        @Override
        public Type getGenericType() {
            return methods.get(0).getGenericReturnType();
        }

        @Override
        public String getSourceName() {
            return methodName;
        }

        @Override
        @SneakyThrows
        public Object getDefaultValue(Object instance) {
            try {
                return methods.get(0).invoke(instance);
            } catch (Exception ex) {
                throw new RuntimeException("Unable to evaluate default value for method: " + methods.get(0).getName() +
                                           " of instance: " + instance.getClass().getSimpleName() + ". Msg: " + CommonUtils.getErrorMessage(ex));
            }
        }

        @Override
        public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass, boolean isOnlyFirstOccurrence) {
            if (isOnlyFirstOccurrence) {
                if (!methods.isEmpty()) {
                    if (methods.get(0).isAnnotationPresent(annotationClass)) {
                        return methods.get(0).getDeclaredAnnotation(annotationClass);
                    }
                }
                return null;
            }
            return UIFieldUtils.getDeclaredAnnotationFromMethods(annotationClass, methods);
        }

        @Override
        public <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass) {
            return UIFieldUtils.getDeclaredAnnotationsByType(annotationClass, methods);
        }

        @Override
        public String toString() {
            return "UIFieldMethodContext{name=%stype=%s}".formatted(getName(), getType());
        }
    }

    public record UIFieldStringTemplateRecord(boolean as, boolean ap, boolean hst) {
    }
}

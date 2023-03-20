package org.touchhome.app.utils;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.touchhome.bundle.api.util.TouchHomeUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.OneToMany;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.app.builder.ui.UIInputBuilderImpl;
import org.touchhome.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.touchhome.app.model.UIFieldClickToEdit;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldFunction;
import org.touchhome.app.model.entity.widget.UIFieldMarkers;
import org.touchhome.app.model.entity.widget.UIFieldOptionColor;
import org.touchhome.app.model.entity.widget.UIFieldOptionFontSize;
import org.touchhome.app.model.entity.widget.UIFieldOptionVerticalAlign;
import org.touchhome.app.model.entity.widget.UIFieldPadding;
import org.touchhome.app.model.entity.widget.UIFieldTimeSlider;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.model.var.UIFieldVariable;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.entity.validation.UIFieldValidationSize;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldExpand;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreParent;
import org.touchhome.bundle.api.ui.field.UIFieldLayout;
import org.touchhome.bundle.api.ui.field.UIFieldLinkToEntity;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldOrder;
import org.touchhome.bundle.api.ui.field.UIFieldPort;
import org.touchhome.bundle.api.ui.field.UIFieldPosition;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldTableLayout;
import org.touchhome.bundle.api.ui.field.UIFieldTitleRef;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIKeyValueField;
import org.touchhome.bundle.api.ui.field.action.ActionInputParameter;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.action.UIActionInput;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuUploadAction;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorBgRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorBooleanMatch;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorMatch;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.touchhome.bundle.api.ui.field.condition.UIFieldDisableCreateTab;
import org.touchhome.bundle.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.image.UIFieldImage;
import org.touchhome.bundle.api.ui.field.image.UIFieldImageSrc;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEditEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntities;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityEditWidth;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldDevicePortSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassListSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectNoValue;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldStaticSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicRequestType;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.touchhome.bundle.api.util.SecureString;
import org.touchhome.bundle.api.util.TouchHomeUtils;

public class UIFieldUtils {

    public static DynamicRequestType fetchRequestWidgetType(Object requestedEntity,
        Class<?> sourceClassType) {
        DynamicRequestType dynamicRequestType = null;
        if (requestedEntity instanceof HasDynamicParameterFields) {
            dynamicRequestType = ((HasDynamicParameterFields) requestedEntity).getDynamicRequestType(sourceClassType);
        }
        return dynamicRequestType == null ? DynamicRequestType.Default : dynamicRequestType;
    }

    public static Collection<UIInputEntity> fetchUIActionsFromClass(Class<?> clazz, EntityContext entityContext) {
        if (clazz != null) {
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIContextMenuAction.class)) {
                UIContextMenuAction action = method.getDeclaredAnnotation(UIContextMenuAction.class);
                if (action.inputs().length > 0) {
                    ((UIInputBuilderImpl) uiInputBuilder).addOpenDialogSelectableButtonInternal(action.value(), action.icon(),
                        action.iconColor(), null, null).editDialog(dialogLayoutBuilder -> {
                        for (UIActionInput actionInput : action.inputs()) {
                            ((UIDialogLayoutBuilderImpl) dialogLayoutBuilder).addInput(actionInput);
                        }
                    });
                } else {
                    uiInputBuilder.addSelectableButton(action.value(), action.icon(), action.iconColor(), null).setText(action.value());
                }
            }
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIContextMenuUploadAction.class)) {
                UIContextMenuUploadAction action = method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
                uiInputBuilder.addSimpleUploadButton(action.value(), action.icon(), action.iconColor(), action.supportedFormats(),
                    null, 0);
            }
            return uiInputBuilder.buildAll();
        }
        return Collections.emptyList();
    }

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType, EntityContext entityContext) {
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>(), entityContext);
    }

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType,
        Set<EntityUIMetaData> entityUIMetaDataSet,
        EntityContext entityContext) {
        if (entityClassByType == null) {
            return Collections.emptyList();
        }
        Object instance = TouchHomeUtils.newInstance(entityClassByType);
        if (instance == null) {
            throw new NotFoundException("Unable to find empty constructor for class: " + entityClassByType.getName());
        }
        return fillEntityUIMetadataList(instance, entityUIMetaDataSet, entityContext, false);
    }

    public static List<EntityUIMetaData> fillEntityUIMetadataList(Object instance, Set<EntityUIMetaData> entityUIMetaDataSet,
        EntityContext entityContext, boolean fullDisableEdit) {
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
            generateUIField(instance, entityUIMetaDataSet, fieldMethodContext, entityContext, fullDisableEdit);
        }

        Set<String> processedMethods = fieldNameToGetters.values().stream().map(UIFieldMethodContext::getMethodName).collect(toSet());

        FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
            if (!processedMethods.contains(field.getName())) { // skip if already managed by methods
                String capitalizeMethodName = StringUtils.capitalize(field.getName());
                List<Method> fieldGetterMethods =
                    fieldNameToGettersMap.getOrDefault("get" + capitalizeMethodName, new ArrayList<>());
                fieldGetterMethods.addAll(fieldNameToGettersMap.getOrDefault("is" + capitalizeMethodName, Collections.emptyList()));
                generateUIField(instance, entityUIMetaDataSet, new UIFieldFieldContext(field, fieldGetterMethods), entityContext, fullDisableEdit);
            }
        });

        return new ArrayList<>(entityUIMetaDataSet);
    }

    @SneakyThrows
    private static void generateUIField(Object instance, Set<EntityUIMetaData> entityUIMetaDataList,
        UIFieldContext fieldContext, EntityContext entityContext, boolean fullDisableEdit) {
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
        entityUIMetaData.setHideOnEmpty(nullIfFalse(field.hideOnEmpty()));

        entityUIMetaData.setHideInEdit(nullIfFalse(field.hideInEdit()));
        entityUIMetaData.setDisableEdit(nullIfFalse(field.disableEdit()));
        entityUIMetaData.setHideInView(nullIfFalse(field.hideInView()));

        entityUIMetaData.setStyle(field.style());

        // make sense keep defaultValue(for revert) only if able to edit value
        if (fieldContext.isAnnotationPresent(UIFieldReadDefaultValue.class) &&
            !fullDisableEdit && !field.disableEdit()) {
            entityUIMetaData.setDefaultValue(fieldContext.getDefaultValue(instance));
        }

        ObjectNode jsonTypeMetadata = OBJECT_MAPPER.createObjectNode();

        if (field.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum()
                || fieldContext.isAnnotationPresent(UIFieldTreeNodeSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldDevicePortSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldStaticSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldEntityByClassListSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldClassSelection.class)
                || fieldContext.isAnnotationPresent(UIFieldBeanSelection.class)) {
                // detect types
                UIFieldType fieldType = (field.hideInEdit() || field.disableEdit()) ? UIFieldType.String : UIFieldType.SelectBox;

                if (fieldContext.isAnnotationPresent(UIFieldSelection.class) && fieldContext.getDeclaredAnnotation(UIFieldSelection.class)
                                                                                            .allowInputRawText()) {
                    fieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (fieldContext.isAnnotationPresent(UIFieldStaticSelection.class) && fieldContext.getDeclaredAnnotation(UIFieldStaticSelection.class)
                                                                                                  .allowInputRawText()) {
                    fieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (fieldContext.isAnnotationPresent(UIFieldTreeNodeSelection.class) && fieldContext.getDeclaredAnnotation(UIFieldTreeNodeSelection.class)
                                                                                                    .allowInputRawText()) {
                    fieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (fieldContext.isAnnotationPresent(UIFieldDevicePortSelection.class) && fieldContext.getDeclaredAnnotation(UIFieldDevicePortSelection.class)
                                                                                                      .allowInputRawText()) {
                    fieldType = UIFieldType.TextSelectBoxDynamic;
                }

                entityUIMetaData.setType(fieldType.name());
                if (Collection.class.isAssignableFrom(type)) {
                    jsonTypeMetadata.put("multiple", true);
                }
            } else {
                if (genericType instanceof ParameterizedType
                    && Collection.class.isAssignableFrom(type)) {
                    extractSetEntityType(instance, fieldContext, entityUIMetaData, (ParameterizedType) genericType, jsonTypeMetadata);
                } else {
                    if (type.equals(UIFieldProgress.Progress.class)) {
                        entityUIMetaData.setType("Progress");
                    } else if (type.equals(boolean.class)) {
                        entityUIMetaData.setType(UIFieldType.Boolean.name());
                    } else if (type.equals(float.class)) {
                        entityUIMetaData.setType(UIFieldType.Float.name());
                    } else if (type.equals(int.class)) {
                        entityUIMetaData.setType(UIFieldType.Integer.name());
                    } else if (type.equals(SecureString.class)) {
                        entityUIMetaData.setType(UIFieldType.String.name());
                    } else if (UIInputEntity.class.isAssignableFrom(type)) {
                        entityUIMetaData.setType("InputEntity");
                    } else {
                        entityUIMetaData.setType(type.getSimpleName());
                    }
                }
            }
        } else {
            entityUIMetaData.setType(field.type().name());
        }

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

        putIfTrue(jsonTypeMetadata, "fw", field.fullWidth());
        putIfTrue(jsonTypeMetadata, "showLabelInFw", field.hideLabelInFullWidth());
        putIfNonEmpty(jsonTypeMetadata, "bg", field.bg());

        UIFieldPadding fieldPadding = fieldContext.getDeclaredAnnotation(UIFieldPadding.class);
        if (fieldPadding != null) {
            entityUIMetaData.setType("Padding");
        }

        UIFieldIconPicker fieldIconPicker = fieldContext.getDeclaredAnnotation(UIFieldIconPicker.class);
        if (fieldIconPicker != null) {
            entityUIMetaData.setType("IconPicker");
            jsonTypeMetadata.put("allowEmptyIcon", fieldIconPicker.allowEmptyIcon());
            jsonTypeMetadata.put("allowSize", fieldIconPicker.allowSize());
            jsonTypeMetadata.put("allowSpin", fieldIconPicker.allowSpin());
            jsonTypeMetadata.put("allowThreshold", fieldIconPicker.allowThreshold());
            jsonTypeMetadata.put("allowBackground", fieldIconPicker.allowBackground());
        }

        UIFieldVariable fieldVariable = fieldContext.getDeclaredAnnotation(UIFieldVariable.class);
        if (fieldVariable != null) {
            entityUIMetaData.setType("Variable");
        }

        UIFieldTitleRef fieldTitleRef = fieldContext.getDeclaredAnnotation(UIFieldTitleRef.class);
        if (fieldTitleRef != null) {
            assertFieldExists(instance, fieldTitleRef.value());
            jsonTypeMetadata.put("titleRef", fieldTitleRef.value());
        }

        UIFieldPosition fieldPosition = fieldContext.getDeclaredAnnotation(UIFieldPosition.class);
        if (fieldPosition != null) {
            entityUIMetaData.setType("Position");
            jsonTypeMetadata.put("disableCenter", fieldPosition.disableCenter());
        }

        UIFieldFunction fieldFunction = fieldContext.getDeclaredAnnotation(UIFieldFunction.class);
        if (fieldFunction != null) {
            jsonTypeMetadata.put("func", fieldFunction.value());
        }

        UIFieldColorPicker fieldColorPicker = fieldContext.getDeclaredAnnotation(UIFieldColorPicker.class);
        if (fieldColorPicker != null) {
            entityUIMetaData.setType(UIFieldType.ColorPicker.name());
            jsonTypeMetadata.put("allowThreshold", fieldColorPicker.allowThreshold());
            jsonTypeMetadata.put("animateColorCondition", fieldColorPicker.animateColorCondition());
        }

        UIFieldTableLayout fieldTableLayout = fieldContext.getDeclaredAnnotation(UIFieldTableLayout.class);
        if (fieldTableLayout != null) {
            entityUIMetaData.setType("TableLayout");
            jsonTypeMetadata.put("maxRows", fieldTableLayout.maxRows());
            jsonTypeMetadata.put("maxColumns", fieldTableLayout.maxColumns());
        }

        if (entityUIMetaData.getType() != null) {
            UIFieldSelectionUtil.handleFieldSelections(
                fieldContext, entityContext, entityUIMetaData, jsonTypeMetadata);
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
            jsonTypeMetadata.put("group", fieldGroup.value());
            if (fieldGroup.order() > 0) {
                jsonTypeMetadata.put("groupOrder", fieldGroup.order());
            }
            if (StringUtils.isNotEmpty(fieldGroup.borderColor())) {
                jsonTypeMetadata.put("borderColor", fieldGroup.borderColor());
            }
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
            entityUIMetaData.setType("Progress");
            jsonTypeMetadata.put("color", fieldProgress.color());
            jsonTypeMetadata.put("bgColor", fieldProgress.fillColor());
            jsonTypeMetadata.set("colorChange", OBJECT_MAPPER.valueToTree(Arrays.stream(fieldProgress.colorChange()).collect(
                Collectors.toMap(UIFieldProgress.UIFieldProgressColorChange::color, UIFieldProgress.UIFieldProgressColorChange::whenMoreThan))));
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

        // TODO: MAKE IT WORKS
        List<UIActionButton> uiActionButtons = fieldContext.getDeclaredAnnotationsByType(UIActionButton.class);
        if (!uiActionButtons.isEmpty()) {
            JSONArray actionButtons = new JSONArray();
            for (UIActionButton actionButton : uiActionButtons) {
                JSONArray inputs = new JSONArray();
                for (UIActionInput actionInput : actionButton.inputs()) {
                    inputs.put(new ActionInputParameter(actionInput).toJson());
                }
                actionButtons.put(
                    new JSONObject()
                        .put("name", actionButton.name())
                        .put("icon", actionButton.icon())
                        .put("color", actionButton.color())
                        .put("inputs", inputs)
                        .put("style", actionButton.style()));
            }
            jsonTypeMetadata.set("actionButtons", OBJECT_MAPPER.valueToTree(actionButtons));
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
            if (fieldColorBgRef.animate()) {
                jsonTypeMetadata.put("colorBgRefAnimate", true);
            }
        }

        var fieldExpand = fieldContext.getDeclaredAnnotation(UIFieldExpand.class);
        if (fieldExpand != null && type.isAssignableFrom(List.class)) {
            jsonTypeMetadata.put("expand", "true");
        }

        var fieldLink = fieldContext.getDeclaredAnnotation(UIFieldLinkToEntity.class);
        if (fieldLink != null) {
            entityUIMetaData.setNavLink(getClassEntityNavLink(field, fieldLink.value()));
        }

        var fieldSelectValueOnEmpty = fieldContext.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
        if (fieldSelectValueOnEmpty != null) {
            ObjectNode selectValueOnEmpty = OBJECT_MAPPER.createObjectNode();
            selectValueOnEmpty.put("color", fieldSelectValueOnEmpty.color());
            selectValueOnEmpty.put("label", fieldSelectValueOnEmpty.label());
            selectValueOnEmpty.put("icon", fieldSelectValueOnEmpty.icon());
            jsonTypeMetadata.set("selectValueOnEmpty", selectValueOnEmpty);
        }

        var fieldSelectNoValue = fieldContext.getDeclaredAnnotation(UIFieldSelectNoValue.class);
        if (fieldSelectNoValue != null) {
            jsonTypeMetadata.put("optionsNotFound", fieldSelectNoValue.value());
        }

        if (String.class.getSimpleName().equals(entityUIMetaData.getType())) {
            UIKeyValueField uiKeyValueField = fieldContext.getDeclaredAnnotation(UIKeyValueField.class);
            if (uiKeyValueField != null) {
                jsonTypeMetadata.put("maxSize", uiKeyValueField.maxSize());
                jsonTypeMetadata.set("keyType", OBJECT_MAPPER.valueToTree(uiKeyValueField.keyType()));
                jsonTypeMetadata.set("valueType", OBJECT_MAPPER.valueToTree(uiKeyValueField.valueType()));
                jsonTypeMetadata.put("defaultKey", uiKeyValueField.defaultKey());
                jsonTypeMetadata.put("defaultValue", uiKeyValueField.defaultValue());
                jsonTypeMetadata.put("keyFormat", uiKeyValueField.keyFormat());
                jsonTypeMetadata.put("valueFormat", uiKeyValueField.valueFormat());
                jsonTypeMetadata.put("keyValueType", uiKeyValueField.keyValueType().name());
                jsonTypeMetadata.put("showKey", uiKeyValueField.showKey());
                entityUIMetaData.setType("KeyValue");
            }
        }

        assembleTextOptions(fieldContext, sourceName, jsonTypeMetadata);

        var fieldNumber = fieldContext.getDeclaredAnnotation(UIFieldNumber.class);
        if (fieldNumber != null) {
            jsonTypeMetadata.put("min", fieldNumber.min());
            jsonTypeMetadata.put("max", fieldNumber.max());
        }

        var fieldSlider = fieldContext.getDeclaredAnnotation(UIFieldSlider.class);
        if (fieldSlider != null) {
            jsonTypeMetadata.put("min", fieldSlider.min());
            jsonTypeMetadata.put("max", fieldSlider.max());
            jsonTypeMetadata.put("step", fieldSlider.step());
            entityUIMetaData.setType(UIFieldType.Slider.name());
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
            jsonTypeMetadata.put("inlineGroupEditable", fieldInlineGroup.editable());
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
                putUIInlineFieldIfRequire(instance, fieldContext, entityUIMetaData, (ParameterizedType) genericType, jsonTypeMetadata, entityContext);
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
                    entityContext);

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

        if (jsonTypeMetadata.size() != 0) {
            entityUIMetaData.setTypeMetaData(jsonTypeMetadata.toString());
        }
        entityUIMetaData.setOrder(field.order());
        if (fieldContext.isAnnotationPresent(UIFieldOrder.class)) {
            entityUIMetaData.setOrder(fieldContext.getDeclaredAnnotation(UIFieldOrder.class).value());
        }
        entityUIMetaData.setRequired(nullIfFalse(field.required()));

        if (BaseEntity.class.isAssignableFrom(type) && type.getDeclaredAnnotation(UISidebarMenu.class) != null) {
            entityUIMetaData.setNavLink(getClassEntityNavLink(field, type));
        }
        if (entityUIMetaData.getType() == null) {
            throw new RuntimeException("Unable to evaluate field '" + sourceName + "' type for class: " + instance.getClass().getSimpleName());
        }

        entityUIMetaDataList.remove(entityUIMetaData);
        entityUIMetaDataList.add(entityUIMetaData);
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

    private static void putIfNonEmpty(ObjectNode metadata, String key, String value) {
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
            if (InternalUtil.findMethodByName(instance.getClass(), fieldName) == null) {
                throw new ServerException("Unable to find field <" + fieldName + ">");
            }
        }
    }

    private static String getClassEntityNavLink(UIField field, Class<?> entityClass) {
        UISidebarMenu uiSidebarMenu = entityClass.getDeclaredAnnotation(UISidebarMenu.class);
        if (uiSidebarMenu == null) {
            throw new IllegalArgumentException(
                "Unable to create link for field: "
                    + field.name()
                    + " and class: "
                    + entityClass.getSimpleName());
        }
        String href =            StringUtils.defaultIfEmpty(                uiSidebarMenu.overridePath(), entityClass.getSimpleName());
        return uiSidebarMenu.parent().name().toLowerCase() + "/" + href;
    }

    private static void putUIInlineFieldIfRequire(Object instance, UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData,
        ParameterizedType genericType, ObjectNode jsonTypeMetadata, EntityContext entityContext) {
        if (!jsonTypeMetadata.has("inlineTypeFields")) {
            Type inlineType = extractSetEntityType(instance, fieldContext, entityUIMetaData, genericType, jsonTypeMetadata);
            // for non BaseEntity types
            if (inlineType == null) {
                inlineType = genericType.getActualTypeArguments()[0];
            }
            Object childClassInstance = TouchHomeUtils.newInstance((Class) inlineType);
            boolean fullDisableEdit = fieldContext.getUIField().disableEdit();
            jsonTypeMetadata.set("inlineTypeFields",
                OBJECT_MAPPER.valueToTree(UIFieldUtils.fillEntityUIMetadataList(childClassInstance, new HashSet<>(), entityContext, fullDisableEdit)));
        }
    }

    private static Boolean nullIfFalse(boolean value) {
        return value ? true : null;
    }

    private static Type extractSetEntityType(Object instance, UIFieldContext fieldContext, EntityUIMetaData entityUIMetaData,
        ParameterizedType genericType, ObjectNode jsonTypeMetadata) {
        Type typeArgument = genericType.getActualTypeArguments()[0];
        if (!trySetListType(typeArgument, entityUIMetaData, fieldContext, jsonTypeMetadata)) {
            typeArgument = findClassGenericClass(typeArgument, instance);
            if (typeArgument != null && !trySetListType(typeArgument, entityUIMetaData, fieldContext, jsonTypeMetadata)) {
                entityUIMetaData.setType(UIFieldType.Chips.name());
                jsonTypeMetadata.put("type", ((Class) typeArgument).getSimpleName());
            } else {
                typeArgument = null;
            }
        }
        return typeArgument;
    }

    private static boolean trySetListType(Type argument, EntityUIMetaData entityUIMetaData, UIFieldContext fieldContext, ObjectNode jsonTypeMetadata) {
        if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
            entityUIMetaData.setType("List");
            if (fieldContext.isAnnotationPresent(MaxItems.class)) {
                jsonTypeMetadata.put("max", fieldContext.getDeclaredAnnotation(MaxItems.class).value());
            }
            jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
            jsonTypeMetadata.put("mappedBy", fieldContext.getDeclaredAnnotation(OneToMany.class).mappedBy());
            return true;
        }
        return false;
    }

    private static Type findClassGenericClass(Type argument, Object instance) {
        if (argument instanceof TypeVariable) {
            String genericTypeName = ((TypeVariable) argument).getName();
            Type[] classGenericTypes = ((ParameterizedType) instance.getClass().getGenericSuperclass()).getActualTypeArguments();
            TypeVariable<? extends Class<?>>[] parameters = instance.getClass().getSuperclass().getTypeParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getName().equals(genericTypeName)) {
                    return classGenericTypes[i];
                }
            }
        }
        return null;
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
    }

    public static class UIFieldMethodContext implements UIFieldContext {

        private final String name;
        @Getter private final String methodName;
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
            this.name = getDeclaredAnnotation(UIField.class).name();
            this.methodName = InternalUtil.getMethodShortName(fieldMethod);
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
            return methods.stream().filter(m -> m.isAnnotationPresent(UIField.class)).findFirst().get()
                          .getDeclaredAnnotation(UIField.class);
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
                    " of instance: " + instance.getClass().getSimpleName() + ". Msg: " + TouchHomeUtils.getErrorMessage(ex));
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
    }
}

package org.touchhome.app.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.v1.UIInputBuilderImpl;
import org.touchhome.app.manager.common.v1.layout.UIDialogLayoutBuilderImpl;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.entity.widget.ability.ClassDescription;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.action.*;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.color.*;
import org.touchhome.bundle.api.ui.field.image.UIFieldImage;
import org.touchhome.bundle.api.ui.field.image.UIFieldImageSrc;
import org.touchhome.bundle.api.ui.field.selection.*;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicRequestType;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.touchhome.bundle.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.api.util.SecureString;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;

import javax.persistence.OneToMany;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class UIFieldUtils {

    @RequiredArgsConstructor
    private enum SelectHandler {
        simple(UIFieldSelection.class, params -> {
            params.classEntityForDynamicOptionLoader = params.classEntityForDynamicOptionLoader == null ? params.classEntity :
                    params.classEntityForDynamicOptionLoader;
            UIFieldSelection uiFieldTargetSelection = params.field.getDeclaredAnnotation(UIFieldSelection.class);
            if (uiFieldTargetSelection != null) {
                params.targetClass = uiFieldTargetSelection.value();
            }
            Collection<OptionModel> options =
                    fetchOptionsFromDynamicOptionLoader(params.targetClass, params.classEntityForDynamicOptionLoader,
                            params.entityContext, uiFieldTargetSelection, params.deps);
            if (options != null) {
                return options;
            }

            if (params.targetClass.isEnum()) {
                return OptionModel.enumList((Class<? extends Enum>) params.targetClass);
            }
            return null;
        }), bean(UIFieldBeanSelection.class,
                params -> params.entityContext.getBeansOfTypeWithBeanName(params.targetClass).keySet().stream()
                        .map(OptionModel::key).collect(Collectors.toList())),
        port(UIFieldDevicePortSelection.class, params -> OptionModel.listOfPorts(false)),
        clazz(UIFieldClassSelection.class, params -> {
            ClassFinder classFinder = params.entityContext.getBean(ClassFinder.class);
            UIFieldClassSelection uiFieldClassSelection = params.field.getDeclaredAnnotation(UIFieldClassSelection.class);
            List<Class<?>> list = new ArrayList<>();
            for (String basePackage : uiFieldClassSelection.basePackages()) {
                list.addAll(classFinder.getClassesWithParent(uiFieldClassSelection.value(), null, basePackage));
            }
            Predicate<Class<?>> predicate = CommonUtils.newInstance(uiFieldClassSelection.filter());
            return list.stream().filter(predicate).map(c -> OptionModel.of(c.getName(), c.getSimpleName()))
                    .collect(Collectors.toList());
        }), entityByClass(UIFieldEntityByClassSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            for (UIFieldEntityByClassSelection item : params.field.getDeclaredAnnotationsByType(
                    UIFieldEntityByClassSelection.class)) {
                Class<? extends HasEntityIdentifier> sourceClassType = item.value();
                for (Class<? extends HasEntityIdentifier> foundTargetType : params.entityContext.getClassesWithParent(
                        sourceClassType, item.basePackages())) {
                    if (BaseEntity.class.isAssignableFrom(foundTargetType)) {
                        for (BaseEntity baseEntity : params.entityContext.findAll((Class<BaseEntity>) foundTargetType)) {
                            list.add(addEntityToSelection(baseEntity, params.classEntityForDynamicOptionLoader, sourceClassType));
                        }
                    }
                }
            }
            return list;
        });

        private final Class<? extends Annotation> selectClass;
        private final Function<LoadOptionsParameters, Collection<OptionModel>> handler;
    }

    public static Collection<OptionModel> loadOptions(Object classEntity, EntityContext entityContext, String fieldName,
                                                      Object classEntityForDynamicOptionLoader, String selectType,
                                                      Map<String, String> deps, String param0) {
        List<? extends Class<? extends Annotation>> selectAnnotations =
                Stream.of(SelectHandler.values()).map(h -> h.selectClass).collect(Collectors.toList());
        Method method = findMethodByName(classEntity.getClass(), fieldName, selectAnnotations);
        if (method == null) {
            // maybe method returns enum for selection
            method = getMethod(classEntity.getClass(), fieldName);
        }
        if (method != null) {
            Collection<OptionModel> options =
                    loadOptions(method, entityContext, method.getReturnType(), classEntity, classEntityForDynamicOptionLoader,
                            selectType, deps, param0);
            if (options != null) {
                return options;
            }
        }
        Field field = FieldUtils.getField(classEntity.getClass(), fieldName, true);
        if (field != null) {
            return loadOptions(field, entityContext, field.getType(), classEntity, classEntityForDynamicOptionLoader, selectType,
                    deps, param0);
        }
        throw new NotFoundException(
                "Unable to find select handler for entity type: " + classEntity.getClass().getSimpleName() + " and fieldName: " +
                        fieldName);
    }

    @AllArgsConstructor
    private static class LoadOptionsParameters {
        private AccessibleObject field;
        private EntityContext entityContext;
        private Class<?> targetClass;
        private Object classEntityForDynamicOptionLoader;
        private Object classEntity;
        private Map<String, String> deps;
        private String param0;
    }

    @SneakyThrows
    private static Collection<OptionModel> loadOptions(AccessibleObject field, EntityContext entityContext, Class<?> targetClass,
                                                       Object classEntity, Object classEntityForDynamicOptionLoader,
                                                       String selectType, Map<String, String> deps, String param0) {
        LoadOptionsParameters param =
                new LoadOptionsParameters(field, entityContext, targetClass, classEntity, classEntityForDynamicOptionLoader, deps,
                        param0);

        if (selectType != null) {
            SelectHandler selectHandler = SelectHandler.valueOf(selectType);
            if (field.getDeclaredAnnotationsByType(selectHandler.selectClass).length > 0) {
                Collection<OptionModel> result = selectHandler.handler.apply(param);
                if (result != null) {
                    return result;
                }
            }
        }

        for (SelectHandler selectHandler : SelectHandler.values()) {
            if (field.getDeclaredAnnotationsByType(selectHandler.selectClass).length > 0) {
                return selectHandler.handler.apply(param);
            }
        }

        return null;
    }

    private static Collection<OptionModel> fetchOptionsFromDynamicOptionLoader(Class<?> targetClass,
                                                                               Object classEntityForDynamicOptionLoader,
                                                                               EntityContext entityContext,
                                                                               UIFieldSelection uiFieldTargetSelection,
                                                                               Map<String, String> deps) {
        DynamicOptionLoader dynamicOptionLoader = null;
        if (DynamicOptionLoader.class.isAssignableFrom(targetClass)) {
            dynamicOptionLoader = (DynamicOptionLoader) CommonUtils.newInstance(targetClass);
            if (dynamicOptionLoader == null) {
                throw new RuntimeException("Unable to instantiate DynamicOptionLoader class: " + targetClass.getName() +
                        ". Does class has public modifier and no args constructor?");
            }
        }
        if (dynamicOptionLoader != null) {
            BaseEntity<?> baseEntity =
                    classEntityForDynamicOptionLoader instanceof BaseEntity ? (BaseEntity<?>) classEntityForDynamicOptionLoader :
                            null;
            return dynamicOptionLoader.loadOptions(
                    new DynamicOptionLoader.DynamicOptionLoaderParameters(baseEntity, entityContext,
                            uiFieldTargetSelection.staticParameters(), deps));
        }
        return null;
    }

    private static OptionModel addEntityToSelection(HasEntityIdentifier entityIdentifier, Object requestedEntity,
                                                    Class<? extends HasEntityIdentifier> sourceClassType) {
        OptionModel optionModel = OptionModel.of(entityIdentifier.getEntityID(), entityIdentifier.getTitle());
        List<Method> classDescriptionMethods = MethodUtils.getMethodsListWithAnnotation(sourceClassType, ClassDescription.class);
        Method descriptionMethod = classDescriptionMethods.isEmpty() ? null : classDescriptionMethods.iterator().next();
        String entityTypeDescription;
        try {
            entityTypeDescription = descriptionMethod == null ? null : (String) descriptionMethod.invoke(entityIdentifier);
            if (entityTypeDescription != null) {
                optionModel.setDescription(entityTypeDescription);
            }
        } catch (Exception ignore) {
        }

        if (entityIdentifier instanceof SelectionWithDynamicParameterFields) {
            SelectionWithDynamicParameterFields.RequestDynamicParameter requestDynamicParameter =
                    new SelectionWithDynamicParameterFields.RequestDynamicParameter(requestedEntity,
                            fetchRequestWidgetType(requestedEntity, sourceClassType));

            optionModel.json(params -> {
                DynamicParameterFields dynamicParameterFields =
                        ((SelectionWithDynamicParameterFields) entityIdentifier).getDynamicParameterFields(
                                requestDynamicParameter);
                if (dynamicParameterFields != null) {
                    try {
                        params.put("dynamicParameter", new JSONObject().put("groupName", dynamicParameterFields.getGroupName())
                                .put("borderColor", dynamicParameterFields.getBorderColor())
                                .put("defaultValues", CommonUtils.OBJECT_MAPPER.writeValueAsString(dynamicParameterFields))
                                .put("class", dynamicParameterFields.getClass().getSimpleName())
                                .put("holder", "dynamicParameterFieldsHolder"));
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
        }
        return optionModel;
    }

    public static DynamicRequestType fetchRequestWidgetType(Object requestedEntity,
                                                            Class<? extends HasEntityIdentifier> sourceClassType) {
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
                    uiInputBuilder.addSelectableButton(action.value(), action.icon(), action.iconColor(), null);
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
        Object instance = CommonUtils.newInstance(entityClassByType);
        if (instance == null) {
            throw new NotFoundException("Unable to find empty constructor for class: " + entityClassByType.getName());
        }
        return fillEntityUIMetadataList(instance, entityUIMetaDataSet, entityContext);
    }

    public static List<EntityUIMetaData> fillEntityUIMetadataList(Object instance, Set<EntityUIMetaData> entityUIMetaDataSet,
                                                                  EntityContext entityContext) {
        Map<String, List<Method>> fieldNameToGetters = new HashMap<>();

        List<Class<?>> classes = getAllSuperclassesAndInterfaces(instance.getClass());
        classes.add(0, instance.getClass());
        for (final Class<?> cursor : classes) {
            for (Method declaredMethod : cursor.getDeclaredMethods()) {
                fieldNameToGetters.putIfAbsent(declaredMethod.getName(), new ArrayList<>());
                fieldNameToGetters.get(declaredMethod.getName()).add(declaredMethod);
            }
        }

        // filter methods with @UIField
        Map<String, UIFieldMethodContext> uiFieldNameToGetters = new HashMap<>();
        for (Method uiFieldMethod : MethodUtils.getMethodsListWithAnnotation(instance.getClass(), UIField.class, true, false)) {
            if (!uiFieldNameToGetters.containsKey(uiFieldMethod.getName())) {
                uiFieldNameToGetters.put(uiFieldMethod.getName(),
                        new UIFieldMethodContext(uiFieldMethod, fieldNameToGetters.get(uiFieldMethod.getName())));
            }
        }

        for (UIFieldMethodContext uiFieldMethodContext : uiFieldNameToGetters.values()) {
            generateUIField(instance, entityUIMetaDataSet, uiFieldMethodContext, entityContext);
        }

        Set<String> processedMethods =
                uiFieldNameToGetters.values().stream().map(UIFieldMethodContext::getMethodName).collect(Collectors.toSet());

        FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
            if (!processedMethods.contains(field.getName())) { // skip if already managed by methods
                String capitalizeMethodName = StringUtils.capitalize(field.getName());
                List<Method> fieldGetterMethods =
                        fieldNameToGetters.getOrDefault("get" + capitalizeMethodName, new ArrayList<>());
                fieldGetterMethods.addAll(fieldNameToGetters.getOrDefault("is" + capitalizeMethodName, Collections.emptyList()));
                generateUIField(instance, entityUIMetaDataSet, new UIFieldFieldContext(field, fieldGetterMethods), entityContext);
            }
        });

        List<EntityUIMetaData> data = new ArrayList<>(entityUIMetaDataSet);
        Collections.sort(data);

        return data;
    }

    @SneakyThrows
    private static void generateUIField(Object instance, Set<EntityUIMetaData> entityUIMetaDataList,
                                        UIFieldContext uiFieldContext, EntityContext entityContext) {
        EntityUIMetaData entityUIMetaData = new EntityUIMetaData();
        entityUIMetaData.setEntityName(uiFieldContext.getName());
        Type genericType = uiFieldContext.getGenericType();
        String sourceName = uiFieldContext.getSourceName();
        Class<?> type = uiFieldContext.getType();
        UIField uiField = uiFieldContext.getUIField();

        if (uiFieldContext.isAnnotationPresent(UIFieldIgnore.class, true)) {
            entityUIMetaDataList.remove(entityUIMetaData);
            return; // skip transparent UIFields
        }

        entityUIMetaData.setLabel(trimToNull(uiField.label()));
        if (uiField.inlineEdit()) {
            entityUIMetaData.setInlineEdit(true);
        }
        if (uiField.copyButton()) {
            entityUIMetaData.setCopyButton(true);
        }
        if (uiField.inlineEditWhenEmpty()) {
            entityUIMetaData.setInlineEditWhenEmpty(true);
        }
        entityUIMetaData.setColor(trimToNull(uiField.color()));
        entityUIMetaData.setIcon(trimToNull(uiField.icon()));

        if (uiField.readOnly()) {
            entityUIMetaData.setReadOnly(true);
        }
        if (uiField.hideOnEmpty()) {
            entityUIMetaData.setHideOnEmpty(true);
        }
        if (uiField.isRevert()) {
            entityUIMetaData.setRevert(true);
        }
        if (uiField.onlyEdit()) {
            entityUIMetaData.setOnlyEdit(true);
        }
        entityUIMetaData.setStyle(uiField.style());
        entityUIMetaData.setDefaultValue(uiFieldContext.getDefaultValue(instance));

        JSONObject jsonTypeMetadata = new JSONObject();
        if (uiField.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum() || uiFieldContext.isAnnotationPresent(UIFieldFileSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldDevicePortSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldStaticSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldEntityByClassListSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldClassSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldBeanSelection.class)) {
                // detect types
                UIFieldType uiFieldType = uiField.readOnly() ? UIFieldType.String : UIFieldType.SelectBox;

                if (uiFieldContext.isAnnotationPresent(UIFieldSelection.class) &&
                        uiFieldContext.getDeclaredAnnotation(UIFieldSelection.class).allowInputRawText()) {
                    uiFieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (uiFieldContext.isAnnotationPresent(UIFieldStaticSelection.class) &&
                        uiFieldContext.getDeclaredAnnotation(UIFieldStaticSelection.class).allowInputRawText()) {
                    uiFieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (uiFieldContext.isAnnotationPresent(UIFieldFileSelection.class) &&
                        uiFieldContext.getDeclaredAnnotation(UIFieldFileSelection.class).allowInputRawText()) {
                    uiFieldType = UIFieldType.TextSelectBoxDynamic;
                }
                if (uiFieldContext.isAnnotationPresent(UIFieldDevicePortSelection.class) &&
                        uiFieldContext.getDeclaredAnnotation(UIFieldDevicePortSelection.class).allowInputRawText()) {
                    uiFieldType = UIFieldType.TextSelectBoxDynamic;
                }

                entityUIMetaData.setType(uiFieldType.name());
                if (Collection.class.isAssignableFrom(type)) {
                    jsonTypeMetadata.put("multiple", true);
                }
            } else {
                if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                    Type firstArgument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                    if (!trySetListType(firstArgument, entityUIMetaData, uiFieldContext, jsonTypeMetadata)) {
                        Type subArgument = findClassGenericClass(firstArgument, instance);
                        if (!trySetListType(subArgument, entityUIMetaData, uiFieldContext, jsonTypeMetadata)) {
                            // otherwise Set<String>, Set<Integer> set as Chips
                            entityUIMetaData.setType(UIFieldType.Chips.name());
                            jsonTypeMetadata.put("type", ((Class) firstArgument).getSimpleName());
                        }
                    }
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
                    } else {
                        entityUIMetaData.setType(type.getSimpleName());
                    }
                }
            }
        } else {
            entityUIMetaData.setType(uiField.type().name());
        }

        if (uiField.fullWidth()) {
            jsonTypeMetadata.put("fw", true);
        }
        if (uiField.hideLabelInFullWidth()) {
            jsonTypeMetadata.put("showLabelInFw", true);
        }
        if (StringUtils.isNotEmpty(uiField.bg())) {
            jsonTypeMetadata.put("bg", uiField.bg());
        }

        UIFieldIconPicker uiFieldIconPicker = uiFieldContext.getDeclaredAnnotation(UIFieldIconPicker.class);
        if (uiFieldIconPicker != null) {
            entityUIMetaData.setType("IconPicker");
            jsonTypeMetadata.put("allowEmptyIcon", uiFieldIconPicker.allowEmptyIcon());
            jsonTypeMetadata.put("allowThreshold", uiFieldIconPicker.allowThreshold());
        }

        UIFieldPosition uiFieldPosition = uiFieldContext.getDeclaredAnnotation(UIFieldPosition.class);
        if (uiFieldPosition != null) {
            entityUIMetaData.setType("Position");
            jsonTypeMetadata.put("disableCenter", uiFieldPosition.disableCenter());
        }

        UIFieldColorPicker uiFieldColorPicker = uiFieldContext.getDeclaredAnnotation(UIFieldColorPicker.class);
        if (uiFieldColorPicker != null) {
            entityUIMetaData.setType(UIFieldType.ColorPicker.name());
            jsonTypeMetadata.put("allowThreshold", uiFieldColorPicker.allowThreshold());
            jsonTypeMetadata.put("animateColorCondition", uiFieldColorPicker.animateColorCondition());
        }

        UIFieldTableLayout uiFieldTableLayout = uiFieldContext.getDeclaredAnnotation(UIFieldTableLayout.class);
        if (uiFieldTableLayout != null) {
            entityUIMetaData.setType("TableLayout");
            jsonTypeMetadata.put("maxRows", uiFieldTableLayout.maxRows());
            jsonTypeMetadata.put("maxColumns", uiFieldTableLayout.maxColumns());
        }

        handleFieldSelections(uiFieldContext, entityContext, entityUIMetaData, jsonTypeMetadata);

        UIFieldGroup uiFieldGroup = uiFieldContext.getDeclaredAnnotation(UIFieldGroup.class);
        if (uiFieldGroup != null) {
            jsonTypeMetadata.put("group", uiFieldGroup.value());
            jsonTypeMetadata.put("groupOrder", uiFieldGroup.order());
            jsonTypeMetadata.put("borderColor", uiFieldGroup.borderColor());
        }

        Pattern pattern = uiFieldContext.getDeclaredAnnotation(Pattern.class);
        if (pattern != null) {
            jsonTypeMetadata.put("regexp", pattern.regexp());
            jsonTypeMetadata.put("regexpMsg", pattern.message());
        }

        UIFieldProgress uiFieldProgress = uiFieldContext.getDeclaredAnnotation(UIFieldProgress.class);
        if (uiFieldProgress != null) {
            entityUIMetaData.setType("Progress");
            jsonTypeMetadata.put("color", uiFieldProgress.color());
            jsonTypeMetadata.put("bgColor", uiFieldProgress.fillColor());
            jsonTypeMetadata.put("colorChange", Arrays.stream(uiFieldProgress.colorChange()).collect(
                    Collectors.toMap(UIFieldProgress.UIFieldProgressColorChange::color,
                            UIFieldProgress.UIFieldProgressColorChange::whenMoreThan)));
        }

        UIFieldImage uiFieldImage = uiFieldContext.getDeclaredAnnotation(UIFieldImage.class);
        if (uiFieldImage != null) {
            entityUIMetaData.setType("Image");
            jsonTypeMetadata.put("maxWidth", uiFieldImage.maxWidth());
            jsonTypeMetadata.put("maxHeight", uiFieldImage.maxHeight());
        }

        UIFieldImageSrc uiFieldImageSrc = uiFieldContext.getDeclaredAnnotation(UIFieldImageSrc.class);
        if (uiFieldImageSrc != null) {
            entityUIMetaData.setType("ImageSrc");
            jsonTypeMetadata.put("maxWidth", uiFieldImageSrc.maxWidth());
            jsonTypeMetadata.put("maxHeight", uiFieldImageSrc.maxHeight());
        }

        // TODO: MAKE IT WORKS
        List<UIActionButton> uiActionButtons = uiFieldContext.getDeclaredAnnotationsByType(UIActionButton.class);
        if (!uiActionButtons.isEmpty()) {
            JSONArray actionButtons = new JSONArray();
            for (UIActionButton actionButton : uiActionButtons) {
                JSONArray inputs = new JSONArray();
                for (UIActionInput actionInput : actionButton.inputs()) {
                    inputs.put(new ActionInputParameter(actionInput).toJson());
                }
                actionButtons.put(new JSONObject().put("name", actionButton.name()).put("icon", actionButton.icon())
                        .put("color", actionButton.color()).put("inputs", inputs).put("style", actionButton.style()));
            }
            jsonTypeMetadata.put("actionButtons", actionButtons);
        }
        UIFieldPort uiFieldPort = uiFieldContext.getDeclaredAnnotation(UIFieldPort.class);
        if (uiFieldPort != null) {
            jsonTypeMetadata.put("min", uiFieldPort.min());
            jsonTypeMetadata.put("max", uiFieldPort.max());
            entityUIMetaData.setType("Port");
        }

        List<UIFieldColorMatch> uiFieldColorMatches = uiFieldContext.getDeclaredAnnotationsByType(UIFieldColorMatch.class);
        if (!uiFieldColorMatches.isEmpty()) {
            JSONObject colors = new JSONObject();
            for (UIFieldColorMatch uiFieldColorMatch : uiFieldColorMatches) {
                colors.put(uiFieldColorMatch.value(), uiFieldColorMatch.color());
            }
            jsonTypeMetadata.put("valueColor", colors);
        }

        UIFieldColorStatusMatch uiFieldColorStatusMatch = uiFieldContext.getDeclaredAnnotation(UIFieldColorStatusMatch.class);
        if (uiFieldColorStatusMatch != null) {
            JSONObject colors = new JSONObject();
            colors.put("OFFLINE", uiFieldColorStatusMatch.offline());
            colors.put("ONLINE", uiFieldColorStatusMatch.online());
            colors.put("UNKNOWN", uiFieldColorStatusMatch.unknown());
            colors.put("ERROR", uiFieldColorStatusMatch.error());
            colors.put("REQUIRE_AUTH", uiFieldColorStatusMatch.requireAuth());
            jsonTypeMetadata.put("valueColor", colors);
            jsonTypeMetadata.put("valueColorPrefix", uiFieldColorStatusMatch.handlePrefixes());
        }

        UIFieldColorBooleanMatch uiFieldColorBooleanMatch = uiFieldContext.getDeclaredAnnotation(UIFieldColorBooleanMatch.class);
        if (uiFieldColorBooleanMatch != null) {
            JSONObject colors = new JSONObject();
            colors.put("true", uiFieldColorBooleanMatch.False());
            colors.put("false", uiFieldColorBooleanMatch.True());
            jsonTypeMetadata.put("valueColor", colors);
        }

        UIFieldColorRef uiFieldColorRef = uiFieldContext.getDeclaredAnnotation(UIFieldColorRef.class);
        if (uiFieldColorRef != null) {
            if (instance.getClass().getDeclaredField(uiFieldColorRef.value()) == null) {
                throw new ServerException("Unable to find field <" + uiFieldColorRef.value() + "> declared in UIFieldColorRef");
            }
            jsonTypeMetadata.put("colorRef", uiFieldColorRef.value());
        }

        UIFieldExpand uiFieldExpand = uiFieldContext.getDeclaredAnnotation(UIFieldExpand.class);
        if (uiFieldExpand != null && type.isAssignableFrom(List.class)) {
            jsonTypeMetadata.put("expand", "true");
        }

        UIFieldColorSource uiFieldRowColor = uiFieldContext.getDeclaredAnnotation(UIFieldColorSource.class);
        if (uiFieldRowColor != null) {
            jsonTypeMetadata.put("rc", sourceName);
        }

        UIFieldCreateWorkspaceVariableOnEmpty uiFieldCreateWorkspaceVariable =
                uiFieldContext.getDeclaredAnnotation(UIFieldCreateWorkspaceVariableOnEmpty.class);
        if (uiFieldCreateWorkspaceVariable != null) {
            jsonTypeMetadata.put("cwvoe", "true");
        }

        UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty =
                uiFieldContext.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
        if (uiFieldSelectValueOnEmpty != null) {
            jsonTypeMetadata.put("sveColor", uiFieldSelectValueOnEmpty.color());
            jsonTypeMetadata.put("sveLabel", uiFieldSelectValueOnEmpty.label());
        }

        if (entityUIMetaData.getType().equals(String.class.getSimpleName())) {
            UIKeyValueField uiKeyValueField = uiFieldContext.getDeclaredAnnotation(UIKeyValueField.class);
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

        UIFieldNumber uiFieldNumber = uiFieldContext.getDeclaredAnnotation(UIFieldNumber.class);
        if (uiFieldNumber != null) {
            jsonTypeMetadata.put("min", uiFieldNumber.min());
            jsonTypeMetadata.put("max", uiFieldNumber.max());
        }

        UIFieldSlider uiFieldSlider = uiFieldContext.getDeclaredAnnotation(UIFieldSlider.class);
        if (uiFieldSlider != null) {
            jsonTypeMetadata.put("min", uiFieldSlider.min());
            jsonTypeMetadata.put("max", uiFieldSlider.max());
            jsonTypeMetadata.put("step", uiFieldSlider.step());
            entityUIMetaData.setType(UIFieldType.Slider.name());
        }

        UIFieldCodeEditor uiFieldCodeEditor = uiFieldContext.getDeclaredAnnotation(UIFieldCodeEditor.class);
        if (uiFieldCodeEditor != null) {
            jsonTypeMetadata.put("wordWrap", uiFieldCodeEditor.wordWrap());
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

    private static void handleFieldSelections(UIFieldContext uiFieldContext, EntityContext entityContext,
                                              EntityUIMetaData entityUIMetaData, JSONObject jsonTypeMetadata) {
        UIFieldBeanSelection uiFieldBeanSelection = uiFieldContext.getDeclaredAnnotation(UIFieldBeanSelection.class);
        if (uiFieldBeanSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", SelectHandler.bean.name());
            if (!uiFieldBeanSelection.lazyLoading()) {
                Set<String> values = entityContext.getBeansOfTypeWithBeanName(uiFieldBeanSelection.value()).keySet();
                meta.put("selectOptions",
                        uiFieldBeanSelection.addEmpty() ? OptionModel.listWithEmpty(values) : OptionModel.list(values));
            }
        }

        if (uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)
                || uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", SelectHandler.entityByClass.name());
        }

        UIFieldClassSelection uiFieldClassSelection = uiFieldContext.getDeclaredAnnotation(UIFieldClassSelection.class);
        if (uiFieldClassSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", "static");
        }

        UIFieldStaticSelection uiFieldStaticSelection = uiFieldContext.getDeclaredAnnotation(UIFieldStaticSelection.class);
        if (uiFieldStaticSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectOptions", uiFieldStaticSelection.value());
        }

        if (uiFieldContext.getType().isEnum() && uiFieldContext.getType().getEnumConstants().length < 10) {
            List<OptionModel> optionModels = OptionModel.enumList((Class<? extends Enum>) uiFieldContext.getType());
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectOptions", optionModels);
        }

        UIFieldSelection uiFieldSelection = uiFieldContext.getDeclaredAnnotation(UIFieldSelection.class);
        if (uiFieldSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", SelectHandler.simple.name());
            meta.put("lazyLoading", uiFieldSelection.lazyLoading());
            meta.put("parentChildJoiner", uiFieldSelection.parentChildJoiner());

            if (uiFieldSelection.dependencyFields().length > 0) {
                jsonTypeMetadata.put("depFields", uiFieldSelection.dependencyFields());
            }
        }

        UIFieldDevicePortSelection uiFieldDevicePortSelection =
                uiFieldContext.getDeclaredAnnotation(UIFieldDevicePortSelection.class);
        if (uiFieldDevicePortSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", SelectHandler.port.name());
            meta.put("icon", uiFieldDevicePortSelection.icon());
            meta.put("iconColor", uiFieldDevicePortSelection.iconColor());
        }

        UIFieldFileSelection uiFieldFileSelection = uiFieldContext.getDeclaredAnnotation(UIFieldFileSelection.class);
        if (uiFieldFileSelection != null) {
            JSONObject meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);
            meta.put("selectType", "file");
            meta.put("SAFS", uiFieldFileSelection.showAllFileSystems());
            meta.put("ASD", uiFieldFileSelection.allowSelectDirs());
            meta.put("AMS", uiFieldFileSelection.allowMultiSelect());
            meta.put("ASF", uiFieldFileSelection.allowSelectFiles());
            meta.put("pattern", uiFieldFileSelection.pattern());
            meta.put("icon", uiFieldFileSelection.icon());
            meta.put("iconColor", uiFieldFileSelection.iconColor());
        }
    }

    private static JSONObject getTextBoxSelections(EntityUIMetaData entityUIMetaData, JSONObject jsonTypeMetadata) {
        if (entityUIMetaData.getType().equals(UIFieldType.TextSelectBoxDynamic.name())) {
            if (!jsonTypeMetadata.has("textBoxSelections")) {
                jsonTypeMetadata.put("textBoxSelections", new JSONArray());
            }
            JSONArray jsonArray = jsonTypeMetadata.getJSONArray("textBoxSelections");
            JSONObject subMeta = new JSONObject();
            jsonArray.put(subMeta);
            return subMeta;
        }
        return jsonTypeMetadata;
    }

    private static boolean trySetListType(Type argument, EntityUIMetaData entityUIMetaData, UIFieldContext uiFieldContext,
                                          JSONObject jsonTypeMetadata) {
        if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
            entityUIMetaData.setType("List");
            if (uiFieldContext.isAnnotationPresent(MaxItems.class)) {
                jsonTypeMetadata.put("max", uiFieldContext.getDeclaredAnnotation(MaxItems.class).value());
            }
            jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
            jsonTypeMetadata.put("mappedBy", uiFieldContext.getDeclaredAnnotation(OneToMany.class).mappedBy());
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

    public interface UIFieldContext {
        String getName();

        Class<?> getType();

        UIField getUIField();

        Type getGenericType();

        String getSourceName();

        default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return !getDeclaredAnnotationsByType(annotationClass).isEmpty();
        }

        default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass, boolean isOnlyFirstOccurrence) {
            return getDeclaredAnnotation(annotationClass, isOnlyFirstOccurrence) != null;
        }

        Object getDefaultValue(Object instance);

        default <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
            return getDeclaredAnnotation(annotationClass, false);
        }

        <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass, boolean isOnlyFirstOccurrence);

        <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass);
    }

    @RequiredArgsConstructor
    public static class UIFieldFieldContext implements UIFieldContext {

        private final Field field;
        private final List<Method> fieldGetterMethods;

        @Override
        public String getName() {
            return StringUtils.defaultIfEmpty(field.getAnnotation(UIField.class).name(), field.getName());
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
        @Getter
        private final String methodName;
        private final List<Method> methods = new ArrayList<>();

        public UIFieldMethodContext(Method uiFieldMethod, List<Method> allMethods) {
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
            this.methodName = InternalUtil.getMethodShortName(uiFieldMethod);
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
            if (methods.get(0).isAnnotationPresent(UIFieldIgnoreGetDefault.class)) {
                return null;
            }
            return methods.get(0).invoke(instance);
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

    private static Method findMethodByName(Class clz, String name,
                                           List<? extends Class<? extends Annotation>> annotationClasses) {
        Method method = getMethod(clz, name);
        if (method != null) {
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (method.isAnnotationPresent(annotationClass)) {
                    return method;
                }
            }
            if (clz.getSuperclass() != null) {
                Method superMethod = findMethodByName(clz.getSuperclass(), name, annotationClasses);
                if (superMethod != null) {
                    return superMethod;
                }
            }
        }
        return null;
    }

    private static Method getMethod(Class clz, String name) {
        String capitalizeName = StringUtils.capitalize(name);
        Method method = MethodUtils.getAccessibleMethod(clz, "get" + capitalizeName);
        if (method == null) {
            method = MethodUtils.getAccessibleMethod(clz, "is" + capitalizeName);
        }
        return method;
    }
}

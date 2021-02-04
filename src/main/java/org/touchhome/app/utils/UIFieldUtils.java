package org.touchhome.app.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.app.json.UIActionDescription;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.action.ActionInputParameter;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.action.UIActionInput;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.color.*;
import org.touchhome.bundle.api.ui.field.image.UIFieldImage;
import org.touchhome.bundle.api.ui.field.image.UIFieldImageSrc;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.OneToMany;
import javax.validation.constraints.Max;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class UIFieldUtils {

    public static Collection<OptionModel> loadOptions(HasEntityIdentifier entity, EntityContext entityContext, String fieldName) {
        Method method = InternalUtil.findMethodByName(entity.getClass(), fieldName);
        if (method != null) {
            UIFieldSelection uiFieldSelection = method.getDeclaredAnnotation(UIFieldSelection.class);
            if (uiFieldSelection != null) {
                return loadOptions(method, entityContext, method.getReturnType(), entity);
            }
            Collection<OptionModel> options = loadOptions(method, entityContext, method.getReturnType(), entity);
            if (!options.isEmpty()) {
                return options;
            }
        }
        Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
        if (field != null) {
            UIFieldSelection uiFieldSelection = field.getDeclaredAnnotation(UIFieldSelection.class);
            if (uiFieldSelection != null) {
                return loadOptions(field, entityContext, field.getType(), entity);
            }
            return loadOptions(field, entityContext, field.getType(), entity);
        }
        throw new ServerException("Unable to find select handler for entity type: " + entity.getClass().getSimpleName()
                + " and fieldName: " + fieldName);
    }

    @SneakyThrows
    private static Collection<OptionModel> loadOptions(AccessibleObject field, EntityContext entityContext, Class<?> targetClass,
                                                       HasEntityIdentifier entity) {
        UIFieldSelection uiFieldTargetSelection = field.getDeclaredAnnotation(UIFieldSelection.class);
        if (uiFieldTargetSelection != null) {
            targetClass = uiFieldTargetSelection.value();
        }
        DynamicOptionLoader dynamicOptionLoader = null;
        if (DynamicOptionLoader.class.isAssignableFrom(targetClass)) {
            dynamicOptionLoader = (DynamicOptionLoader) TouchHomeUtils.newInstance(targetClass);
        }
        if (dynamicOptionLoader != null) {
            return dynamicOptionLoader.loadOptions(entity instanceof BaseEntity ? (BaseEntity<?>) entity : null, entityContext);
        }

        if (targetClass.isEnum()) {
            return OptionModel.enumList((Class<? extends Enum>) targetClass);
        }

        if (field.isAnnotationPresent(UIFieldBeanSelection.class)) {
            return entityContext.getBeansOfTypeWithBeanName(targetClass).keySet()
                    .stream().map(OptionModel::key).collect(Collectors.toList());
        } else if (field.isAnnotationPresent(UIFieldClassSelection.class)) {
            ClassFinder classFinder = entityContext.getBean(ClassFinder.class);
            UIFieldClassSelection uiFieldClassSelection = field.getDeclaredAnnotation(UIFieldClassSelection.class);
            List<Class<?>> list = new ArrayList<>();
            for (String basePackage : uiFieldClassSelection.basePackages()) {
                list.addAll(classFinder.getClassesWithParent(uiFieldClassSelection.value(), null, basePackage));
            }
            Predicate<Class<?>> predicate = TouchHomeUtils.newInstance(uiFieldClassSelection.filter());
            return list.stream().filter(predicate).map(c -> OptionModel.of(c.getName(), c.getSimpleName())).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    public static List<UIActionDescription> fetchUIActionsFromClass(Class<?> clazz) {
        List<UIActionDescription> actions = new ArrayList<>();
        if (clazz != null) {
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIContextMenuAction.class)) {
                UIContextMenuAction action = method.getDeclaredAnnotation(UIContextMenuAction.class);
                JSONArray inputs = new JSONArray();
                for (UIActionInput actionInput : action.inputs()) {
                    inputs.put(new ActionInputParameter(actionInput).toJson());
                }
                JSONObject metadata = inputs.isEmpty() ? null : new JSONObject().put("inputs", inputs);
                actions.add(new UIActionDescription().setType(UIActionDescription.Type.method)
                        .setIcon(action.icon()).setIconColor(action.iconColor())
                        .setMetadata(metadata).setName(action.value()));
            }
        }
        return actions;
    }

    public interface UIFieldContext {
        String getName();

        Class<?> getType();

        UIField getUIField();

        Type getGenericType();

        String getSourceName();

        default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return getDeclaredAnnotation(annotationClass) != null;
        }

        Object getDefaultValue(Object instance);

        <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass);

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
            return fieldGetterMethods.isEmpty() ? FieldUtils.readField(field, instance, true) : fieldGetterMethods.get(0).invoke(instance);
        }

        @Override
        public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
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

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType) {
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>());
    }

    @SneakyThrows
    public static List<EntityUIMetaData> fillEntityUIMetadataList(Class entityClassByType, Set<EntityUIMetaData> entityUIMetaDataSet) {
        if (entityClassByType == null) {
            return Collections.emptyList();
        }
        Object instance = TouchHomeUtils.newInstance(entityClassByType);
        if (instance == null) {
            throw new NotFoundException("Unable to find empty constructor for class: " + entityClassByType.getName());
        }
        return fillEntityUIMetadataList(instance, entityUIMetaDataSet);
    }

    public static List<EntityUIMetaData> fillEntityUIMetadataList(Object instance, Set<EntityUIMetaData> entityUIMetaDataSet) {
        Map<String, List<Method>> fieldNameToGetters = new HashMap<>();
        Class<?> cursor = instance.getClass();
        if (!BaseEntity.class.isAssignableFrom(cursor)) {
            throw new RuntimeException("Unable to fill UIFields with no parent as BaseEntity class");
        }
        while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
            for (Method declaredMethod : cursor.getDeclaredMethods()) {
                fieldNameToGetters.putIfAbsent(declaredMethod.getName(), new ArrayList<>());
                fieldNameToGetters.get(declaredMethod.getName()).add(declaredMethod);
            }
            cursor = cursor.getSuperclass();
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
            generateUIField(instance, entityUIMetaDataSet, uiFieldMethodContext);
        }

        Set<String> processedMethods = uiFieldNameToGetters.values().stream()
                .map(UIFieldMethodContext::getMethodName).collect(Collectors.toSet());

        FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
            if (!processedMethods.contains(field.getName())) { // skip if already managed by methods
                String capitalizeMethodName = StringUtils.capitalize(field.getName());
                List<Method> fieldGetterMethods = fieldNameToGetters.getOrDefault("get" + capitalizeMethodName, new ArrayList<>());
                fieldGetterMethods.addAll(fieldNameToGetters.getOrDefault("is" + capitalizeMethodName, Collections.emptyList()));

                generateUIField(instance, entityUIMetaDataSet, new UIFieldFieldContext(field, fieldGetterMethods));
            }
        });

        List<EntityUIMetaData> data = new ArrayList<>(entityUIMetaDataSet);
        Collections.sort(data);

        return data;
    }

    @SneakyThrows
    private static void generateUIField(Object instance,
                                        Set<EntityUIMetaData> entityUIMetaDataList,
                                        UIFieldContext uiFieldContext) {
        EntityUIMetaData entityUIMetaData = new EntityUIMetaData();
        entityUIMetaData.setEntityName(uiFieldContext.getName());
        Type genericType = uiFieldContext.getGenericType();
        String sourceName = uiFieldContext.getSourceName();
        Class<?> type = uiFieldContext.getType();
        UIField uiField = uiFieldContext.getUIField();

        if (uiFieldContext.isAnnotationPresent(UIFieldIgnore.class)) {
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
        entityUIMetaData.setDefaultValue(uiFieldContext.getDefaultValue(instance));

        JSONObject jsonTypeMetadata = new JSONObject();
        if (uiField.type().equals(UIFieldType.AutoDetect)) {
            if (type.isEnum() ||
                    uiFieldContext.isAnnotationPresent(UIFieldSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldClassSelection.class) ||
                    uiFieldContext.isAnnotationPresent(UIFieldBeanSelection.class)) {
                entityUIMetaData.setType(uiField.readOnly() ? UIFieldType.String.name() : UIFieldType.Selection.name());
            } else {
                if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
                    Type argument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                    if (!trySetListType(argument, entityUIMetaData, uiFieldContext, jsonTypeMetadata)) {
                        argument = findClassGenericClass(argument, instance);
                        trySetListType(argument, entityUIMetaData, uiFieldContext, jsonTypeMetadata);
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

        Pattern pattern = uiFieldContext.getDeclaredAnnotation(Pattern.class);
        if (pattern != null) {
            jsonTypeMetadata.put("regexp", pattern.regexp());
            jsonTypeMetadata.put("regexpMsg", pattern.message());
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
            jsonTypeMetadata.put("valueColor", colors);
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

        UIFieldCreateWorkspaceVariableOnEmpty uiFieldCreateWorkspaceVariable = uiFieldContext.getDeclaredAnnotation(UIFieldCreateWorkspaceVariableOnEmpty.class);
        if (uiFieldCreateWorkspaceVariable != null) {
            jsonTypeMetadata.put("cwvoe", "true");
        }

        UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty = uiFieldContext.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
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

    private static boolean trySetListType(Type argument, EntityUIMetaData entityUIMetaData, UIFieldContext uiFieldContext, JSONObject jsonTypeMetadata) {
        if (argument instanceof Class && BaseEntity.class.isAssignableFrom((Class<?>) argument)) {
            entityUIMetaData.setType("List");
            if (uiFieldContext.isAnnotationPresent(Max.class)) {
                jsonTypeMetadata.put("max", uiFieldContext.getDeclaredAnnotation(Max.class).value());
            }
            jsonTypeMetadata.put("type", ((Class<?>) argument).getSimpleName());
            jsonTypeMetadata.put("mappedBy", uiFieldContext.getDeclaredAnnotation(OneToMany.class).mappedBy());
            return true;
        }
        return false;
    }

    private static Type findClassGenericClass(Type argument, Object instance) {
        String genericTypeName = ((TypeVariable) argument).getName();
        Type[] classGenericTypes = ((ParameterizedType) instance.getClass().getGenericSuperclass()).getActualTypeArguments();
        TypeVariable<? extends Class<?>>[] parameters = instance.getClass().getSuperclass().getTypeParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(genericTypeName)) {
                return classGenericTypes[i];
            }
        }
        return null;
    }

    public static class UIFieldMethodContext implements UIFieldContext {

        private final String name;
        @Getter
        private final String methodName;
        private final List<Method> methods = new ArrayList<>();

        public UIFieldMethodContext(Method uiFieldMethod, List<Method> allMethods) {
            this.name = uiFieldMethod.getAnnotation(UIField.class).name();
            this.methodName = InternalUtil.getMethodShortName(uiFieldMethod);
            for (Method method : allMethods) {
                // when we see @UIFieldIgnoreParent than ignore super annotations
                if (method.isAnnotationPresent(UIFieldIgnoreParent.class)) {
                    break;
                }
                methods.add(method);
            }
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
            return methods.stream().filter(m -> m.isAnnotationPresent(UIField.class)).findFirst().get().getDeclaredAnnotation(UIField.class);
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
            return methods.get(0).invoke(instance);
        }

        @Override
        public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
            return UIFieldUtils.getDeclaredAnnotationFromMethods(annotationClass, methods);
        }

        @Override
        public <A extends Annotation> List<A> getDeclaredAnnotationsByType(Class<A> annotationClass) {
            return UIFieldUtils.getDeclaredAnnotationsByType(annotationClass, methods);
        }
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
}

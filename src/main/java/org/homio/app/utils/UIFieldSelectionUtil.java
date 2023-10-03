package org.homio.app.utils;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.utils.UIFieldUtils.buildDynamicParameterMetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.entity.widget.ability.SelectDataSourceDescription;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.BeanSelectionCondition;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.UIFieldListBeanSelection;
import org.homio.api.ui.field.selection.UIFieldDevicePortSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldEntityTypeSelection;
import org.homio.api.ui.field.selection.UIFieldSelectionParent;
import org.homio.api.ui.field.selection.UIFieldStaticSelection;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields.RequestDynamicParameter;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public final class UIFieldSelectionUtil {

    private static final List<? extends Class<? extends Annotation>> SELECT_ANNOTATIONS =
        Stream.of(SelectHandler.values()).map(h -> h.selectClass).collect(Collectors.toList());

    public static List<OptionModel> loadOptions(Object classEntity, EntityContext entityContext, String fieldName, Object classEntityForDynamicOptionLoader,
        String selectType, Map<String, String> deps) {

        Method method = findMethodByName(classEntity.getClass(), fieldName, SELECT_ANNOTATIONS);
        if (method == null) {
            // maybe method returns enum for selection
            method = InternalUtil.findMethodByName(classEntity.getClass(), fieldName);
        }
        List<OptionModel> options = null;
        if (method != null) {
            options = loadOptions(method, entityContext, method.getReturnType(), classEntity, classEntityForDynamicOptionLoader, selectType, deps);
        }
        if (options == null) {
            Field field = FieldUtils.getField(classEntity.getClass(), fieldName, true);
            if (field != null) {
                options = loadOptions(field, entityContext, field.getType(), classEntity, classEntityForDynamicOptionLoader, selectType, deps);
            }
        }

        if (options != null) {
            OptionModel.sort(options);
            return options;
        }

        throw new NotFoundException(
            "Unable to find select handler for entity type: " + classEntity.getClass().getSimpleName() + " and fieldName: " + fieldName);
    }

    @SneakyThrows
    public static List<OptionModel> groupingOptions(List<OptionModel> options) {
        List<OptionModel> result = new ArrayList<>();
        Map<SelectionParent, List<OptionModel>> groupedModels = buildGroupOptionsBySelectionParent(options, result);
        Map<String, OptionModel> parentModels = new HashMap<>();
        for (Map.Entry<SelectionParent, List<OptionModel>> entry : groupedModels.entrySet()) {
            buildGroupOption(result, parentModels, entry, entry.getKey());
        }
        result.addAll(parentModels.values());
        return result;
    }

    public static List<OptionModel> filterOptions(List<OptionModel> options) {
        if (!options.isEmpty()) {
            OptionModel parent = OptionModel.of("");
            parent.addChild(OptionModel.of("empty")); // need to initialize children
            filterResultOptions(parent, options);
            options = parent.getChildren();
        }
        return options;
    }

    public static void assembleItemsToOptions(
        @NotNull List<OptionModel> list,
        @Nullable Class<? extends HasEntityIdentifier> sourceClassType,
        @NotNull List<? extends BaseEntity> items,
        @NotNull EntityContext entityContext,
        @Nullable Object classEntityForDynamicOptionLoader) {
        for (BaseEntity baseEntity : items) {
            String lastValue = tryFetchCurrentValueFromEntity(baseEntity, entityContext);
            String title = baseEntity.getTitle();
            OptionModel optionModel = OptionModel.of(baseEntity.getEntityID(), title);
            if (lastValue != null) {
                optionModel.json(jsonNodes -> jsonNodes.put("cv", lastValue));
            }
            if (baseEntity instanceof HasStatusAndMsg status) {
                optionModel.setStatus(status);
            }
            baseEntity.configureOptionModel(optionModel);
            if (baseEntity instanceof UIFieldDynamicSelection.SelectionConfiguration conf) {
                optionModel.setIcon(conf.selectionIcon().getIcon());
                optionModel.setColor(conf.selectionIcon().getColor());
            } else if (baseEntity.getClass().isAnnotationPresent(UISidebarChildren.class)) {
                UISidebarChildren sc = baseEntity.getClass().getAnnotation(UISidebarChildren.class);
                optionModel.setIcon(sc.icon());
                optionModel.setColor(sc.color());
            }

            updateSelectedOptionModel(baseEntity, classEntityForDynamicOptionLoader, sourceClassType, optionModel);
            list.add(optionModel);
        }
    }

    static void handleFieldSelections(UIFieldUtils.UIFieldContext uiFieldContext, ObjectNode jsonTypeMetadata) {
        List<OptionModel> selectOptions = new ArrayList<>();

        UIFieldListBeanSelection selectionList = uiFieldContext.getDeclaredAnnotation(UIFieldListBeanSelection.class);
        var beanSelections =
            selectionList != null ? Arrays.asList(selectionList.value()) : uiFieldContext.getDeclaredAnnotationsByType(UIFieldBeanSelection.class);
        if (!beanSelections.isEmpty()) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.bean.name());
        }

        if (uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)
            || uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.entityByClass.name());
        }

        if (uiFieldContext.isAnnotationPresent(UIFieldEntityTypeSelection.class)) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.entityByType.name());
        }

        UIFieldStaticSelection staticSelection = uiFieldContext.getDeclaredAnnotation(UIFieldStaticSelection.class);
        if (staticSelection != null) {
            selectOptions.addAll(OptionModel.list(staticSelection.value()));
        }

        if (uiFieldContext.getType().isEnum()
            && uiFieldContext.getType().getEnumConstants().length < 20) {
            selectOptions.addAll(OptionModel.enumList((Class<? extends Enum>) uiFieldContext.getType()));
        }

        if (!selectOptions.isEmpty()) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", "inline");
            meta.set("selectOptions", OBJECT_MAPPER.valueToTree(selectOptions));
        }

        List<UIFieldDynamicSelection> selections = uiFieldContext.getDeclaredAnnotationsByType(UIFieldDynamicSelection.class);
        if (!selections.isEmpty()) {
            for (UIFieldDynamicSelection selection : selections) {
                ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
                meta.put("selectType", SelectHandler.simple.name());
                meta.putPOJO("icon", evaluateIcon(selection.icon(), selection.iconColor(), jsonTypeMetadata));
                if (selection.dependencyFields().length > 0) {
                    // TODO: not tested
                    meta.set("depFields", OBJECT_MAPPER.valueToTree(selection.dependencyFields()));
                }
            }
        }

        UIFieldDevicePortSelection devicePortSelection = uiFieldContext.getDeclaredAnnotation(UIFieldDevicePortSelection.class);
        if (devicePortSelection != null) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.port.name());
        }

        UIFieldTreeNodeSelection fileSelection = uiFieldContext.getDeclaredAnnotation(UIFieldTreeNodeSelection.class);
        if (fileSelection != null) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", "file");
            meta.put("attachMetadata", fileSelection.isAttachMetadata());
            if (fileSelection.isAttachMetadata()) {
                meta.put("prefix", fileSelection.prefix())
                    .put("prefixColor", fileSelection.prefixColor());
            }
            meta.putPOJO("icon", evaluateIcon(fileSelection.icon(), fileSelection.iconColor(), jsonTypeMetadata));

            ObjectNode parameters = OBJECT_MAPPER.createObjectNode();
            parameters.set("fileSystemIds", OBJECT_MAPPER.valueToTree(fileSelection.fileSystemIds()));
            parameters.put("rootPath", fileSelection.rootPath())
                      .put("ASD", fileSelection.allowSelectDirs())
                      .put("AMS", fileSelection.allowMultiSelect())
                      .put("ASF", fileSelection.allowSelectFiles())
                      .put("pattern", fileSelection.pattern())
                      .put("dialogTitle", fileSelection.dialogTitle());
            meta.set("treeParameters", parameters);
        }
    }

    private static Icon evaluateIcon(String icon, String color, ObjectNode jsonTypeMetadata) {
        if(jsonTypeMetadata.has("selectConfig")) {
            JsonNode iconNode = jsonTypeMetadata.get("selectConfig").path("icon");
            if(StringUtils.isEmpty(icon)) {
                icon = iconNode.asText();
            }
            if(StringUtils.isEmpty(color)) {
                color = iconNode.asText();
            }
        }
        return new Icon(icon, color);
    }

    private static List<OptionModel> loadOptions(AccessibleObject field, EntityContext entityContext, Class<?> targetClass, Object classEntity,
        Object classEntityForDynamicOptionLoader, String selectType, Map<String, String> deps) {

        LoadOptionsParameters param = new LoadOptionsParameters(field, entityContext, targetClass, classEntity, classEntityForDynamicOptionLoader, deps);
        // fetch all options according to all selectType
        List<OptionModel> options = new ArrayList<>();
        boolean handled = selectType != null && assembleOptionsBySelectType(field, selectType, param, options);

        // if not passed selectType - iterate through all
        if (options.isEmpty()) {
            for (SelectHandler selectHandler : SelectHandler.values()) {
                if (field.getDeclaredAnnotationsByType(selectHandler.selectClass).length > 0) {
                    handled = true;
                    options = selectHandler.handler.apply(param);
                    break;
                }
            }
        }

        if (!handled) {
            return null;
        }

        return filterAndGroupingOptions(options);
    }

    private static List<OptionModel> filterAndGroupingOptions(List<OptionModel> options) {
        // filter options
        options = filterOptions(options);

        // group by @UIFieldSelectionParent
        return groupingOptions(options);
    }

    private static void buildGroupOption(List<OptionModel> result, Map<String, OptionModel> parentModels, Entry<SelectionParent, List<OptionModel>> entry,
        SelectionParent parent) {
        OptionModel parentModel = OptionModel.of(parent.key, parent.getName()).setIcon(parent.icon).setColor(parent.iconColor)
                                             .setDescription(parent.description).setChildren(entry.getValue());
        if (parent.getParent() != null) {
            JsonNode superParent = entry.getKey().getParent();
            String superParentKey = superParent.get("key").asText();
            OptionModel optionModel = parentModels.computeIfAbsent(superParentKey, key ->
                OptionModel.of(key, superParent.get("name").asText())
                           .setIcon(superParent.path("icon").asText())
                           .setColor(superParent.path("iconColor").asText())
                           .setDescription(superParent.path("description").asText()));
            optionModel.addChild(parentModel);
        } else {
            result.add(parentModel);
        }
    }

    @NotNull
    private static Map<SelectionParent, List<OptionModel>> buildGroupOptionsBySelectionParent(List<OptionModel> options, List<OptionModel> result)
        throws JsonProcessingException {
        Map<SelectionParent, List<OptionModel>> groupedModels = new HashMap<>();
        for (OptionModel option : options) {
            JsonNode parent = option.getJson() == null ? null : option.getJson().remove("sp");
            if (parent != null) {
                SelectionParent selectionParent = OBJECT_MAPPER.treeToValue(parent, SelectionParent.class);
                groupedModels.putIfAbsent(selectionParent, new ArrayList<>());
                groupedModels.get(selectionParent).add(option);
            } else {
                result.add(option);
            }
        }
        return groupedModels;
    }

    private static boolean assembleOptionsBySelectType(AccessibleObject field, String selectType, LoadOptionsParameters param, List<OptionModel> options) {
        boolean handled = false;
        SelectHandler selectHandler = SelectHandler.valueOf(selectType);
        if (field.getDeclaredAnnotationsByType(selectHandler.selectClass).length > 0) {
            Collection<OptionModel> result = selectHandler.handler.apply(param);
            handled = true;
            if (result != null) {
                options.addAll(result);
            }
        }
        return handled;
    }

    @SneakyThrows
    public static Collection<OptionModel> getAllOptions(EntityContextImpl entityContext) {
        LoadOptionsParameters param = new LoadOptionsParameters(null, entityContext, Object.class, new Object(), null, null);
        List<OptionModel> options = new ArrayList<>();
        assembleOptionsForEntityByClassSelection(param, options, HasGetStatusValue.class);
        return filterAndGroupingOptions(options);
    }

    private static List<OptionModel> fetchOptionsFromDynamicOptionLoader(
        Class<?> targetClass, Object classEntityForDynamicOptionLoader,
        EntityContext entityContext,
        UIFieldDynamicSelection uiFieldTargetSelection, Map<String, String> deps) {
        DynamicOptionLoader dynamicOptionLoader = null;
        if (DynamicOptionLoader.class.isAssignableFrom(targetClass)) {
            dynamicOptionLoader = (DynamicOptionLoader) CommonUtils.newInstance(targetClass);
        }
        if (dynamicOptionLoader != null) {
            BaseEntity baseEntity = classEntityForDynamicOptionLoader instanceof BaseEntity ? (BaseEntity) classEntityForDynamicOptionLoader : null;
            return dynamicOptionLoader.loadOptions(
                new DynamicOptionLoader.DynamicOptionLoaderParameters(baseEntity, entityContext, uiFieldTargetSelection.staticParameters(), deps));
        }
        return null;
    }

    private static void buildSelectionsFromBean(List<UIFieldBeanSelection> selections, EntityContext entityContext, List<OptionModel> selectOptions) {
        for (UIFieldBeanSelection selection : selections) {
            for (Map.Entry<String, ?> entry :
                entityContext.getBeansOfTypeWithBeanName(selection.value()).entrySet()) {
                Object bean = entry.getValue();
                // filter if bean is BeanSelectionCondition and visible is false
                if (bean instanceof BeanSelectionCondition cond) {
                    if (!cond.isBeanVisibleForSelection()) {
                        continue;
                    }
                }
                OptionModel optionModel = OptionModel.of(entry.getKey());
                updateSelectedOptionModel(bean, null, selection.value(), optionModel);
                selectOptions.add(optionModel);
            }
        }
    }

    private static ObjectNode getSelectBoxList(ObjectNode jsonTypeMetadata) {
        if (!jsonTypeMetadata.has("textBoxSelections")) {
            jsonTypeMetadata.set("textBoxSelections", OBJECT_MAPPER.createArrayNode());
        }
        ArrayNode jsonArray = (ArrayNode) jsonTypeMetadata.get("textBoxSelections");
        ObjectNode subMeta = OBJECT_MAPPER.createObjectNode();
        jsonArray.add(subMeta);
        return subMeta;
    }

    public static void updateSelectedOptionModel(Object target, Object requestedEntity, Class<?> sourceClassType, OptionModel optionModel) {
        setDescription(target, sourceClassType, optionModel);

        SelectionParent item = new SelectionParent();
        UIFieldSelectionParent selectionParent = target.getClass().getDeclaredAnnotation(UIFieldSelectionParent.class);
        if (selectionParent != null) {
            item.key = selectionParent.value();
            item.icon = selectionParent.icon();
            item.iconColor = selectionParent.iconColor();
            item.description = selectionParent.description();
        }

        if (target instanceof UIFieldSelectionParent.SelectionParent parent) {
            item.mergeFrom(parent);

            UIFieldSelectionParent.SelectionParent superParent = parent.getSuperParent();
            if (superParent != null) {
                item.parent = OBJECT_MAPPER.valueToTree(new SelectionParent().mergeFrom(superParent));
            }
        }

        if (item.key != null) {
            optionModel.json(json -> json.set("sp", OBJECT_MAPPER.valueToTree(item)));
        }

        if (target instanceof SelectionWithDynamicParameterFields) {
            val requestDynamicParameter = new RequestDynamicParameter(requestedEntity, buildDynamicParameterMetadata(requestedEntity, sourceClassType));

            optionModel.json(json -> {
                val dynamicParameterFields = ((SelectionWithDynamicParameterFields) target).getDynamicParameterFields(requestDynamicParameter);
                if (dynamicParameterFields != null) {
                    DynamicParameter dynamicParameter =
                        new DynamicParameter(
                            dynamicParameterFields.getGroupName(),
                            dynamicParameterFields.getBorderColor(),
                            dynamicParameterFields.getClass().getSimpleName(),
                            dynamicParameterFields);
                    json.set("dynamicParameter", OBJECT_MAPPER.valueToTree(dynamicParameter));
                }
            });
        }
    }

    private static void setDescription(@NotNull Object target, @Nullable Class<?> sourceClassType, @NotNull OptionModel optionModel) {
        String entityTypeDescription = null;
        if (sourceClassType != null) {
            List<Method> classDescriptionMethods = MethodUtils.getMethodsListWithAnnotation(
                sourceClassType, SelectDataSourceDescription.class);
            Method descriptionMethod = classDescriptionMethods.isEmpty() ? null : classDescriptionMethods.iterator().next();
            try {
                entityTypeDescription = descriptionMethod == null ? null : (String) descriptionMethod.invoke(target);
            } catch (Exception ignore) {
            }
        }
        if (StringUtils.isEmpty(entityTypeDescription) && target instanceof DeviceBaseEntity dbe) {
            entityTypeDescription = dbe.getDescription();
        }
        if (entityTypeDescription != null) {
            optionModel.setDescription(Lang.getServerMessage(entityTypeDescription));
        }
    }

    private static void filterResultOptions(OptionModel parent, Collection<OptionModel> options) {
        Map<String, List<OptionModel>> groupByKeyModels = options.stream().collect(groupingBy(OptionModel::getKey));
        for (OptionModel optionModel : options) {
            if (optionModel.getChildren() != null) {
                filterResultOptions(optionModel, optionModel.getChildren());
            }
        }
        List<OptionModel> children = parent.getChildren();
        if (children != null) {
            children.clear();
            children.addAll(groupByKeyModels.values().stream().flatMap(Collection::stream).toList());
        }
    }

    private static Method findMethodByName(Class<?> clz, String name, List<? extends Class<? extends Annotation>> annotationClasses) {
        Method method = InternalUtil.findMethodByName(clz, name);
        if (method != null) {
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (method.isAnnotationPresent(annotationClass)) {
                    return method;
                }
            }
            if (clz.getSuperclass() != null) {
                return findMethodByName(clz.getSuperclass(), name, annotationClasses);
            }
        }
        return null;
    }

    private static void assembleOptionsForEntityByClassSelection(LoadOptionsParameters params, List<OptionModel> list,
        Class<? extends HasEntityIdentifier> sourceClassType) {

        for (Class<? extends HasEntityIdentifier> foundTargetType : params.entityContext.getClassesWithParent(sourceClassType)) {
            if (BaseEntity.class.isAssignableFrom(foundTargetType)) {
                List<BaseEntity> items = params.entityContext.findAll((Class<BaseEntity>) foundTargetType)
                                                             .stream().filter(baseEntity -> {
                        // hack: check if sourceClassType is HasSetStatusValue and we if we are unable to write to value
                        return !HasSetStatusValue.class.isAssignableFrom(sourceClassType) || ((HasSetStatusValue) baseEntity).isAbleToSetValue();
                    }).collect(Collectors.toList());
                assembleItemsToOptions(list, sourceClassType, items, params.entityContext, params.classEntityForDynamicOptionLoader);
            }
        }
    }

    private static String tryFetchCurrentValueFromEntity(BaseEntity baseEntity, EntityContext entityContext) {
        if (baseEntity instanceof HasGetStatusValue) {
            try {
                return ((HasGetStatusValue) baseEntity).getStatusValueRepresentation(entityContext);
            } catch (Exception ex) {
                log.warn("Unable to fetch state value from entity: {}. Msg: {}", baseEntity, CommonUtils.getErrorMessage(ex));
            }
        }
        return null;
    }

    @RequiredArgsConstructor
    public enum SelectHandler {
        simple(UIFieldDynamicSelection.class, params -> {
            params.classEntityForDynamicOptionLoader =
                params.classEntityForDynamicOptionLoader == null ? params.classEntity : params.classEntityForDynamicOptionLoader;
            UIFieldDynamicSelection uiFieldTargetSelection = params.field.getDeclaredAnnotation(UIFieldDynamicSelection.class);
            if (uiFieldTargetSelection != null) {
                params.targetClass = uiFieldTargetSelection.value();
            }
            List<OptionModel> options = fetchOptionsFromDynamicOptionLoader(params.targetClass, params.classEntityForDynamicOptionLoader, params.entityContext,
                uiFieldTargetSelection, params.deps);
            if (options == null) {
                options = OptionModel.enumList((Class<? extends Enum>) params.targetClass);
            }

            return options;
        }),
        bean(UIFieldBeanSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            var selections = Arrays.asList(params.field.getDeclaredAnnotationsByType(UIFieldBeanSelection.class));
            buildSelectionsFromBean(selections, params.entityContext, list);
            return list;
        }),
        port(UIFieldDevicePortSelection.class, params ->
            OptionModel.listOfPorts(false)),
        entityByClass(UIFieldEntityByClassSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            for (UIFieldEntityByClassSelection item : params.field.getDeclaredAnnotationsByType(UIFieldEntityByClassSelection.class)) {
                assembleOptionsForEntityByClassSelection(params, list, item.value());
            }
            return list;
        }),
        entityByType(UIFieldEntityTypeSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            UIFieldEntityTypeSelection item = params.field.getDeclaredAnnotation(UIFieldEntityTypeSelection.class);
            Class<? extends HasEntityIdentifier> typeClass = EntityContextServiceImpl.entitySelectMap.get(item.type());
            if (typeClass == null) {
                throw new IllegalArgumentException("Unable to find entity class with type: " + item.type());
            }
            assembleOptionsForEntityByClassSelection(params, list, typeClass);
            return list;
        });

        private final Class<? extends Annotation> selectClass;
        private final Function<LoadOptionsParameters, List<OptionModel>> handler;
    }

    @AllArgsConstructor
    private static class LoadOptionsParameters {

        private AccessibleObject field;
        private EntityContext entityContext;
        private Class<?> targetClass;
        private Object classEntityForDynamicOptionLoader;
        private Object classEntity;
        private Map<String, String> deps;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    private static class SelectionParent {

        public JsonNode parent;

        private String key;
        private String name;
        private String icon;
        private String iconColor;
        private String description;

        public SelectionParent mergeFrom(UIFieldSelectionParent.SelectionParent parent) {
            key = defaultIfEmpty(parent.getParentId(), key);
            name = defaultIfEmpty(parent.getParentName(), key);
            icon = defaultIfEmpty(parent.getParentIcon(), icon);
            iconColor = defaultIfEmpty(parent.getParentIconColor(), iconColor);
            description = defaultIfEmpty(parent.getParentDescription(), description);
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SelectionParent parent = (SelectionParent) o;

            return key.equals(parent.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    @Getter
    @AllArgsConstructor
    private static class DynamicParameter {

        private final String groupName;
        private final String borderColor;
        private final String targetClass;
        private final DynamicParameterFields defaultValues;
    }
}

package org.homio.app.utils;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.utils.UIFieldSelectionUtil.SELECT_ANNOTATIONS;
import static org.homio.app.utils.UIFieldUtils.buildDynamicParameterMetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.selection.SelectionConfiguration;
import org.homio.api.ui.field.selection.UIFieldSelectionParent;
import org.homio.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields.RequestDynamicParameter;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.utils.UIFieldSelectionUtil.SelectHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public final class OptionUtil {

    @SneakyThrows
    public static Collection<OptionModel> getAllOptions(EntityContextImpl entityContext) {
        LoadOptionsParameters param = new LoadOptionsParameters(null, entityContext, Object.class, new Object(), null, null);
        List<OptionModel> options = new ArrayList<>();
        assembleOptionsForEntityByClassSelection(param, options, HasGetStatusValue.class);
        return filterAndGroupingOptions(options);
    }

    public static List<OptionModel> buildOptions(List<? extends BaseEntity> entities, EntityContextImpl entityContext) {
        List<OptionModel> options = new ArrayList<>();
        assembleItemsToOptions(options, null, entities, entityContext, null);
        return groupingOptions(filterOptions(options));
    }

    public static void assembleOptionsForEntityByClassSelection(LoadOptionsParameters params, List<OptionModel> list,
        Class<? extends HasEntityIdentifier> sourceClassType) {

        for (Class<? extends HasEntityIdentifier> foundTargetType : params.entityContext.getClassesWithParent(sourceClassType)) {
            if (BaseEntity.class.isAssignableFrom(foundTargetType)) {
                List<BaseEntity> items = params.entityContext.findAll((Class<BaseEntity>) foundTargetType)
                                                             .stream().filter(baseEntity -> {
                        // hack: check if sourceClassType is HasSetStatusValue and we if we are unable to write to value
                        return !HasSetStatusValue.class.isAssignableFrom(sourceClassType) || ((HasSetStatusValue) baseEntity).isAbleToSetValue();
                    }).collect(Collectors.toList());
                OptionUtil.assembleItemsToOptions(list, sourceClassType, items, params.entityContext, params.classEntityForDynamicOptionLoader);
            }
        }
    }

    public static List<OptionModel> loadOptions(Object classEntity, EntityContext entityContext, String fieldName, Object classEntityForDynamicOptionLoader,
        String selectType, Map<String, String> deps) {

        Method method = findMethodByName(classEntity.getClass(), fieldName, SELECT_ANNOTATIONS);
        if (method == null) {
            // maybe method returns enum for selection
            method = CommonUtils.findMethodByName(classEntity.getClass(), fieldName);
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

    private static Method findMethodByName(Class<?> clz, String name, List<? extends Class<? extends Annotation>> annotationClasses) {
        Method method = CommonUtils.findMethodByName(clz, name);
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

        return OptionUtil.filterAndGroupingOptions(options);
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

    private static List<OptionModel> filterAndGroupingOptions(List<OptionModel> options) {
        // filter options
        options = filterOptions(options);

        // group by @UIFieldSelectionParent
        return groupingOptions(options);
    }

    public static void assembleItemsToOptions(
        @NotNull List<OptionModel> list,
        @Nullable Class<? extends HasEntityIdentifier> sourceClassType,
        @NotNull List<? extends BaseEntity> items,
        @NotNull EntityContext entityContext,
        @Nullable Object classEntityForDynamicOptionLoader) {
        Collections.sort(items);

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
            String description = null;
            if (baseEntity instanceof SelectionConfiguration conf) {
                optionModel.setIcon(conf.getSelectionIcon().getIcon());
                optionModel.setColor(conf.getSelectionIcon().getColor());
                description = Lang.getServerMessage(conf.getSelectionDescription());
            } else if (baseEntity.getClass().isAnnotationPresent(UISidebarChildren.class)) {
                UISidebarChildren sc = baseEntity.getClass().getAnnotation(UISidebarChildren.class);
                optionModel.setIcon(sc.icon());
                optionModel.setColor(sc.color());
            }
            if (StringUtils.isEmpty(description) && baseEntity instanceof DeviceBaseEntity dbe) {
                description = dbe.getDescription();
            }
            optionModel.setDescription(description);

            updateSelectedOptionModel(baseEntity, classEntityForDynamicOptionLoader, sourceClassType, optionModel);
            list.add(optionModel);
        }
    }

    public static void updateSelectedOptionModel(Object target, Object requestedEntity, Class<?> sourceClassType, OptionModel optionModel) {
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

    public static List<OptionModel> filterOptions(List<OptionModel> options) {
        if (!options.isEmpty()) {
            OptionModel parent = OptionModel.of("");
            parent.addChild(OptionModel.of("empty")); // need to initialize children
            filterResultOptions(parent, options);
            options = parent.getChildren();
        }
        return options;
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

    @SneakyThrows
    private static List<OptionModel> groupingOptions(List<OptionModel> options) {
        List<OptionModel> result = new ArrayList<>();
        Map<SelectionParent, List<OptionModel>> groupedModels = buildGroupOptionsBySelectionParent(options, result);
        Map<String, OptionModel> parentModels = new HashMap<>();
        for (Map.Entry<SelectionParent, List<OptionModel>> entry : groupedModels.entrySet()) {
            buildGroupOption(result, parentModels, entry, entry.getKey());
        }
        result.addAll(parentModels.values());
        return result;
    }

    private static void buildGroupOption(
        List<OptionModel> result,
        Map<String, OptionModel> parentModels,
        Entry<SelectionParent, List<OptionModel>> entry,
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

    @AllArgsConstructor
    public static class LoadOptionsParameters {

        public AccessibleObject field;
        public EntityContext entityContext;
        public Class<?> targetClass;
        public Object classEntityForDynamicOptionLoader;
        public Object classEntity;
        public Map<String, String> deps;
    }
}

package org.homio.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextVar;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.selection.*;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.BeanSelectionCondition;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.UIFieldListBeanSelection;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.impl.ContextServiceImpl;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.utils.OptionUtil.LoadOptionsParameters;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public final class UIFieldSelectionUtil {

    public static final List<? extends Class<? extends Annotation>> SELECT_ANNOTATIONS =
            Stream.of(SelectHandler.values()).map(h -> h.selectClass).collect(Collectors.toList());

    @SneakyThrows
    static void handleFieldSelections(UIFieldUtils.UIFieldContext uiFieldContext, ObjectNode jsonTypeMetadata) {
        List<OptionModel> selectOptions = new ArrayList<>();

        UIFieldListBeanSelection selectionList = uiFieldContext.getDeclaredAnnotation(UIFieldListBeanSelection.class);
        var beanSelections =
                selectionList != null ? Arrays.asList(selectionList.value()) : uiFieldContext.getDeclaredAnnotationsByType(UIFieldBeanSelection.class);
        if (!beanSelections.isEmpty()) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.bean.name());
        }

        if (uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)) {
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

        // TODO: find way to dynamic select if size is heavy
        if (uiFieldContext.getType().isEnum()
            /*&& uiFieldContext.getType().getEnumConstants().length < 20*/) {
            selectOptions.addAll(OptionModel.enumList((Class<? extends Enum>) uiFieldContext.getType()));
        }

        String actualEnum = jsonTypeMetadata.path("actualEnum").asText();
        if (StringUtils.isNotEmpty(actualEnum)) {
            jsonTypeMetadata.remove("actualEnum");
            Class<? extends Enum> enumClass = (Class<? extends Enum>) Class.forName(actualEnum);
            selectOptions.addAll(OptionModel.enumList(enumClass));
        }

        if (!selectOptions.isEmpty()) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", "inline");
            meta.set("selectOptions", OBJECT_MAPPER.valueToTree(selectOptions));
        }

        var dynamicSelections = uiFieldContext.getDeclaredAnnotationsByType(UIFieldDynamicSelection.class);
        for (UIFieldDynamicSelection selection : dynamicSelections) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.simple.name());
            meta.putPOJO("icon", evaluateIcon(selection.icon(), selection.iconColor(), jsonTypeMetadata));
            if (selection.dependencyFields().length > 0) {
                // TODO: not tested
                meta.set("depFields", OBJECT_MAPPER.valueToTree(selection.dependencyFields()));
            }
        }

        var variableSelection = uiFieldContext.getDeclaredAnnotation(UIFieldVariableSelection.class);
        if (variableSelection != null) {
            ObjectNode meta = getSelectBoxList(jsonTypeMetadata);
            meta.put("selectType", SelectHandler.variable.name());
            meta.putPOJO("restriction", variableSelection.varType());
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
        if (jsonTypeMetadata.has("selectConfig")) {
            JsonNode iconNode = jsonTypeMetadata.get("selectConfig").path("icon");
            if (StringUtils.isEmpty(icon)) {
                icon = iconNode.asText();
            }
            if (StringUtils.isEmpty(color)) {
                color = iconNode.asText();
            }
        }
        return new Icon(icon, color);
    }

    private static List<OptionModel> fetchOptionsFromDynamicOptionLoader(
            Class<?> targetClass, Object classEntityForDynamicOptionLoader,
            Context context,
            UIFieldDynamicSelection uiFieldTargetSelection, Map<String, String> deps) {
        DynamicOptionLoader dynamicOptionLoader = null;
        if (DynamicOptionLoader.class.isAssignableFrom(targetClass)) {
            dynamicOptionLoader = (DynamicOptionLoader) CommonUtils.newInstance(targetClass);
        }
        if (dynamicOptionLoader != null) {
            BaseEntity baseEntity = classEntityForDynamicOptionLoader instanceof BaseEntity ? (BaseEntity) classEntityForDynamicOptionLoader : null;
            return dynamicOptionLoader.loadOptions(
                    new DynamicOptionLoader.DynamicOptionLoaderParameters(baseEntity, context, uiFieldTargetSelection.staticParameters(), deps));
        }
        return null;
    }

    private static void buildSelectionsFromBean(List<UIFieldBeanSelection> selections, Context context, List<OptionModel> selectOptions) {
        for (UIFieldBeanSelection selection : selections) {
            for (Map.Entry<String, ?> entry : context.getBeansOfTypeWithBeanName(selection.value()).entrySet()) {
                Object bean = entry.getValue();
                // filter if bean is BeanSelectionCondition and visible is false
                if (bean instanceof BeanSelectionCondition cond) {
                    if (!cond.isBeanVisibleForSelection()) {
                        continue;
                    }
                }
                OptionModel optionModel = OptionModel.of(entry.getKey());
                OptionUtil.updateSelectedOptionModel(bean, null, selection.value(), optionModel);
                selectOptions.add(optionModel);
            }
        }
    }

    public static ObjectNode getSelectBoxList(ObjectNode jsonTypeMetadata) {
        if (!jsonTypeMetadata.has("textBoxSelections")) {
            jsonTypeMetadata.set("textBoxSelections", OBJECT_MAPPER.createArrayNode());
        }
        ArrayNode jsonArray = (ArrayNode) jsonTypeMetadata.get("textBoxSelections");
        ObjectNode subMeta = OBJECT_MAPPER.createObjectNode();
        jsonArray.add(subMeta);
        return subMeta;
    }

    private static @NotNull Predicate<BaseEntity> filterVariable(UIFieldVariableSelection item) {
        return baseEntity -> {
            WorkspaceVariable variable = (WorkspaceVariable) baseEntity;
            if (item.varType() != ContextVar.VariableType.Broadcast && item.requireWritable() && variable.isReadOnly()) {
                return false;
            }

            if (item.varType() == ContextVar.VariableType.Any) {
                return true;
            }

            var bv = variable.getRestriction();
            if (bv == ContextVar.VariableType.Any) {
                return true;
            }

            if (item.varType() == ContextVar.VariableType.Text &&
                (bv == ContextVar.VariableType.Text || bv == ContextVar.VariableType.Enum)) {
                return true;
            }

            if (item.varType() == ContextVar.VariableType.Percentage) {
                if (bv == ContextVar.VariableType.Percentage) {
                    return true;
                } else if (bv == ContextVar.VariableType.Float) {
                    var min = variable.getMin();
                    var max = variable.getMax();
                    return min != null && min >= 0 && max != null && max <= 100;
                }
                return false;
            }

            return bv == item.varType();
        };
    }

    @RequiredArgsConstructor
    public enum SelectHandler {
        simple(UIFieldDynamicSelection.class, params -> {
            params.classEntityForDynamicOptionLoader =
                    params.classEntityForDynamicOptionLoader == null ? params.classEntity : params.classEntityForDynamicOptionLoader;
            var uiFieldTargetSelection = params.field.getDeclaredAnnotation(UIFieldDynamicSelection.class);
            if (uiFieldTargetSelection != null) {
                params.targetClass = uiFieldTargetSelection.value();
            }
            List<OptionModel> options = fetchOptionsFromDynamicOptionLoader(params.targetClass, params.classEntityForDynamicOptionLoader, params.context,
                    uiFieldTargetSelection, params.deps);
            if (options == null) {
                options = OptionModel.enumList((Class<? extends Enum>) params.targetClass);
            }

            return options;
        }),
        bean(UIFieldBeanSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            var selections = Arrays.asList(params.field.getDeclaredAnnotationsByType(UIFieldBeanSelection.class));
            buildSelectionsFromBean(selections, params.context, list);
            return list;
        }),
        port(UIFieldDevicePortSelection.class, params ->
                OptionModel.listOfPorts(false)),
        variable(UIFieldVariableSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            for (UIFieldVariableSelection item : params.field.getDeclaredAnnotationsByType(UIFieldVariableSelection.class)) {
                Predicate<BaseEntity> filter = filterVariable(item);
                List<WorkspaceVariable> items = params.context.db().findAll(WorkspaceVariable.class);
                items = items.stream().filter(filter).collect(Collectors.toList());
                OptionUtil.assembleItemsToOptions(list, WorkspaceVariable.class, items, params.context, params.classEntityForDynamicOptionLoader);
            }
            return list;
        }),
        entityByClass(UIFieldEntityByClassSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            for (UIFieldEntityByClassSelection item : params.field.getDeclaredAnnotationsByType(UIFieldEntityByClassSelection.class)) {
                OptionUtil.assembleOptionsForEntityByClassSelection(params.context, params.classEntityForDynamicOptionLoader, list, item.value());
            }
            return list;
        }),
        entityByType(UIFieldEntityTypeSelection.class, params -> {
            List<OptionModel> list = new ArrayList<>();
            var item = params.field.getDeclaredAnnotation(UIFieldEntityTypeSelection.class);
            var typeClass = ContextServiceImpl.entitySelectMap.get(item.type());
            if (typeClass == null) {
                throw new IllegalArgumentException("Unable to find entity class with type: " + item.type());
            }
            OptionUtil.assembleOptionsForEntityByClassSelection(params.context, params.classEntityForDynamicOptionLoader, list, typeClass);
            return list;
        });

        public final Class<? extends Annotation> selectClass;
        public final Function<LoadOptionsParameters, List<OptionModel>> handler;
    }

}

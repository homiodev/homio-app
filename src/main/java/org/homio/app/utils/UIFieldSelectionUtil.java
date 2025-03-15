package org.homio.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.BeanSelectionCondition;
import org.homio.api.ui.field.selection.UIFieldBeanSelection.UIFieldListBeanSelection;
import org.homio.api.ui.field.selection.UIFieldDevicePortSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldEntityTypeSelection;
import org.homio.api.ui.field.selection.UIFieldStaticSelection;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.impl.ContextServiceImpl;
import org.homio.app.utils.OptionUtil.LoadOptionsParameters;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public final class UIFieldSelectionUtil {

  public static final List<? extends Class<? extends Annotation>> SELECT_ANNOTATIONS =
    Stream.of(SelectHandler.values()).map(h -> h.selectClass).collect(Collectors.toList());

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

  @RequiredArgsConstructor
  public enum SelectHandler {
    simple(UIFieldDynamicSelection.class, params -> {
      params.classEntityForDynamicOptionLoader =
        params.classEntityForDynamicOptionLoader == null ? params.classEntity : params.classEntityForDynamicOptionLoader;
      UIFieldDynamicSelection uiFieldTargetSelection = params.field.getDeclaredAnnotation(UIFieldDynamicSelection.class);
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
    entityByClass(UIFieldEntityByClassSelection.class, params -> {
      List<OptionModel> list = new ArrayList<>();
      for (UIFieldEntityByClassSelection item : params.field.getDeclaredAnnotationsByType(UIFieldEntityByClassSelection.class)) {
        OptionUtil.assembleOptionsForEntityByClassSelection(params.context, params.classEntityForDynamicOptionLoader, list, item.value());
      }
      return list;
    }),
    entityByType(UIFieldEntityTypeSelection.class, params -> {
      List<OptionModel> list = new ArrayList<>();
      UIFieldEntityTypeSelection item = params.field.getDeclaredAnnotation(UIFieldEntityTypeSelection.class);
      Class<? extends HasEntityIdentifier> typeClass = ContextServiceImpl.entitySelectMap.get(item.type());
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

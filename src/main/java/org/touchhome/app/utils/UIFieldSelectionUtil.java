package org.touchhome.app.utils;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.touchhome.app.utils.UIFieldUtils.fetchRequestWidgetType;
import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.SelectDataSourceDescription;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanListSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldDevicePortSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEmptySelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;
import org.touchhome.bundle.api.ui.field.selection.UIFieldStaticSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.touchhome.bundle.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Lang;

public final class UIFieldSelectionUtil {

  private static final List<? extends Class<? extends Annotation>> SELECT_ANNOTATIONS =
      Stream.of(SelectHandler.values()).map(h -> h.selectClass).collect(Collectors.toList());

  @RequiredArgsConstructor
  private enum SelectHandler {
    simple(UIFieldSelection.class, params -> {
      params.classEntityForDynamicOptionLoader = params.classEntityForDynamicOptionLoader == null ? params.classEntity :
          params.classEntityForDynamicOptionLoader;
      UIFieldSelection uiFieldTargetSelection = params.field.getDeclaredAnnotation(UIFieldSelection.class);
      if (uiFieldTargetSelection != null) {
        params.targetClass = uiFieldTargetSelection.value();
      }
      List<OptionModel> options =
          fetchOptionsFromDynamicOptionLoader(params.targetClass, params.classEntityForDynamicOptionLoader,
              params.entityContext, uiFieldTargetSelection, params.deps);
      if (options != null) {
        return options;
      }

      if (params.targetClass.isEnum()) {
        return OptionModel.enumList((Class<? extends Enum>) params.targetClass);
      }
      return null;
    }), bean(UIFieldBeanSelection.class, params -> {
      List<OptionModel> list = new ArrayList<>();
      var selections = Arrays.asList(params.field.getDeclaredAnnotationsByType(UIFieldBeanSelection.class));
      buildSelectionsFromBean(selections, params.entityContext, list);
      return list;
    }), port(UIFieldDevicePortSelection.class, params -> OptionModel.listOfPorts(false)),
    clazz(UIFieldClassSelection.class, params -> {
      return getOptionsForClassSelection(params.entityContext,
          params.field.getDeclaredAnnotation(UIFieldClassSelection.class));
    }), entityByClass(UIFieldEntityByClassSelection.class, params -> {
      List<OptionModel> list = new ArrayList<>();
      UIFieldEntityByClassSelection[] entityClasses =
          params.field.getDeclaredAnnotationsByType(UIFieldEntityByClassSelection.class);
      for (UIFieldEntityByClassSelection item : entityClasses) {
        Class<? extends HasEntityIdentifier> sourceClassType = item.value();
        for (Class<? extends HasEntityIdentifier> foundTargetType : params.entityContext.getClassesWithParent(
            sourceClassType, item.basePackages())) {
          if (BaseEntity.class.isAssignableFrom(foundTargetType)) {
            for (BaseEntity baseEntity : params.entityContext.findAll((Class<BaseEntity>) foundTargetType)) {
              OptionModel optionModel = OptionModel.of(baseEntity.getEntityID(), baseEntity.getTitle());
              if (baseEntity instanceof UIFieldSelection.SelectionConfiguration) {
                var conf = ((UIFieldSelection.SelectionConfiguration) baseEntity);
                optionModel.setIcon(conf.selectionIcon());
                optionModel.setColor(conf.selectionIconColor());
              }

              updateSelectedOptionModel(baseEntity, params.classEntityForDynamicOptionLoader, sourceClassType,
                  optionModel, "entityByClass");
              list.add(optionModel);
            }
          }
        }
      }
      return list;
    });

    private final Class<? extends Annotation> selectClass;
    private final Function<LoadOptionsParameters, List<OptionModel>> handler;
  }

  @NotNull
  private static List<OptionModel> getOptionsForClassSelection(EntityContext entityContext,
      UIFieldClassSelection uiFieldClassSelection) {
    ClassFinder classFinder = entityContext.getBean(ClassFinder.class);
    List<Class<?>> list = new ArrayList<>();
    for (String basePackage : uiFieldClassSelection.basePackages()) {
      list.addAll(classFinder.getClassesWithParent(uiFieldClassSelection.value(), null, basePackage));
    }
    Predicate<Class<?>> predicate = CommonUtils.newInstance(uiFieldClassSelection.filter());
    return list.stream().filter(predicate).map(c -> OptionModel.of(c.getName(), c.getSimpleName()))
        .collect(Collectors.toList());
  }

  public static List<OptionModel> loadOptions(Object classEntity, EntityContext entityContext, String fieldName,
      Object classEntityForDynamicOptionLoader, String[] selectType,
      Map<String, String> deps, String param0) {
    Method method = findMethodByName(classEntity.getClass(), fieldName, SELECT_ANNOTATIONS);
    if (method == null) {
      // maybe method returns enum for selection
      method = InternalUtil.findMethodByName(classEntity.getClass(), fieldName);
    }
    List<OptionModel> options = null;
    if (method != null) {
      options = loadOptions(method, entityContext, method.getReturnType(), classEntity, classEntityForDynamicOptionLoader,
          selectType, deps, param0);
    }
    if (options == null) {
      Field field = FieldUtils.getField(classEntity.getClass(), fieldName, true);
      if (field != null) {
        options = loadOptions(field, entityContext, field.getType(), classEntity, classEntityForDynamicOptionLoader,
            selectType, deps, param0);
      }
    }

    if (options != null) {
      OptionModel.sort(options);
      return options;
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
  private static List<OptionModel> loadOptions(AccessibleObject field, EntityContext entityContext, Class<?> targetClass,
      Object classEntity, Object classEntityForDynamicOptionLoader,
      String[] selectType, Map<String, String> deps, String param0) {
    LoadOptionsParameters param =
        new LoadOptionsParameters(field, entityContext, targetClass, classEntity, classEntityForDynamicOptionLoader, deps,
            param0);

    // fetch all options according to all selectType
    List<OptionModel> options = new ArrayList<>();
    boolean handled = false;
    if (selectType != null) {
      for (String type : selectType) {
        SelectHandler selectHandler = SelectHandler.valueOf(type);
        if (field.getDeclaredAnnotationsByType(selectHandler.selectClass).length > 0) {
          Collection<OptionModel> result = selectHandler.handler.apply(param);
          handled = true;
          if (result != null) {
            options.addAll(result);
          }
        }
      }
    }

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

    // filter options
    if (!options.isEmpty()) {
      OptionModel parent = new OptionModel();
      parent.addChild(OptionModel.of("empty")); // need to initialise children
      filterResultOptions(parent, options);
      options = parent.getChildren();
    }

    // group by @UIFieldSelectionParent
    List<OptionModel> result = new ArrayList<>();
    Map<SelectionParent, List<OptionModel>> groupedModels = new HashMap<>();
    for (OptionModel option : options) {
      SelectionParent parent = option.getJson() == null ? null : (SelectionParent) option.getJson().remove("sp");
      if (parent != null) {
        groupedModels.putIfAbsent(parent, new ArrayList<>());
        groupedModels.get(parent).add(option);
      } else {
        result.add(option);
      }
    }
    for (Map.Entry<SelectionParent, List<OptionModel>> entry : groupedModels.entrySet()) {
      OptionModel optionModel =
          OptionModel.of(entry.getKey().key).setIcon(entry.getKey().icon).setColor(entry.getKey().iconColor)
              .setDescription(entry.getKey().description).setChildren(entry.getValue());
      result.add(optionModel);
    }

    return result;
  }

  private static List<OptionModel> fetchOptionsFromDynamicOptionLoader(Class<?> targetClass,
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

  static void handleFieldSelections(UIFieldUtils.UIFieldContext uiFieldContext, EntityContext entityContext,
      EntityUIMetaData entityUIMetaData, ObjectNode jsonTypeMetadata) {
    Set<String> selectTypes = new HashSet<>();
    List<OptionModel> selectOptions = new ArrayList<>();
    ObjectNode meta = getTextBoxSelections(entityUIMetaData, jsonTypeMetadata);

    UIFieldBeanListSelection beanListSelection = uiFieldContext.getDeclaredAnnotation(UIFieldBeanListSelection.class);
    var beanSelections = beanListSelection != null ? Arrays.asList(beanListSelection.value()) :
        uiFieldContext.getDeclaredAnnotationsByType(UIFieldBeanSelection.class);
    if (!beanSelections.isEmpty()) {
      boolean lazyLoading = beanSelections.stream().anyMatch(UIFieldBeanSelection::lazyLoading);
      if (lazyLoading) {
        selectTypes.add(SelectHandler.bean.name());
      } else {
        buildSelectionsFromBean(beanSelections, entityContext, selectOptions);
      }
    }

    if (uiFieldContext.isAnnotationPresent(UIFieldEmptySelection.class)) {
      meta.put("allowEmptySelection", true);
    }

    if (uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class) ||
        uiFieldContext.isAnnotationPresent(UIFieldEntityByClassSelection.class)) {
      selectTypes.add(SelectHandler.entityByClass.name());
    }

    UIFieldClassSelection classSelection = uiFieldContext.getDeclaredAnnotation(UIFieldClassSelection.class);
    if (classSelection != null) {
      if (classSelection.lazyLoading()) {
        selectTypes.add("static");
      } else {
        selectOptions.addAll(getOptionsForClassSelection(entityContext, classSelection));
      }
    }

    UIFieldStaticSelection staticSelection = uiFieldContext.getDeclaredAnnotation(UIFieldStaticSelection.class);
    if (staticSelection != null) {
      selectOptions.addAll(OptionModel.list(staticSelection.value()));
    }

    if (uiFieldContext.getType().isEnum() && uiFieldContext.getType().getEnumConstants().length < 20) {
      selectOptions.addAll(OptionModel.enumList((Class<? extends Enum>) uiFieldContext.getType()));
    }

    UIFieldSelection selection = uiFieldContext.getDeclaredAnnotation(UIFieldSelection.class);
    if (selection != null) {
      selectTypes.add(SelectHandler.simple.name());
      meta.put("lazyLoading", selection.lazyLoading());
      meta.put("parentChildJoiner", selection.parentChildJoiner());

      if (selection.dependencyFields().length > 0) {
        jsonTypeMetadata.set("depFields", OBJECT_MAPPER.valueToTree(selection.dependencyFields()));
      }
    }

    UIFieldDevicePortSelection devicePortSelection = uiFieldContext.getDeclaredAnnotation(UIFieldDevicePortSelection.class);
    if (devicePortSelection != null) {
      selectTypes.add(SelectHandler.port.name());
      meta.put("icon", devicePortSelection.icon());
      meta.put("iconColor", devicePortSelection.iconColor());
    }

    UIFieldTreeNodeSelection fileSelection = uiFieldContext.getDeclaredAnnotation(UIFieldTreeNodeSelection.class);
    if (fileSelection != null) {
      selectTypes.add("file");
      meta.set("fileSystemIds", OBJECT_MAPPER.valueToTree(fileSelection.fileSystemIds()));
      meta.put("ASD", fileSelection.allowSelectDirs());
      meta.put("AMS", fileSelection.allowMultiSelect());
      meta.put("ASF", fileSelection.allowSelectFiles());
      meta.put("pattern", fileSelection.pattern());
      meta.put("icon", fileSelection.icon());
      meta.put("iconColor", fileSelection.iconColor());
    }

    if (!selectOptions.isEmpty()) {
      meta.set("selectOptions", OBJECT_MAPPER.valueToTree(selectOptions));
    }

    if (!selectTypes.isEmpty()) {
      meta.set("selectType", OBJECT_MAPPER.valueToTree(selectTypes));
    }
  }

  private static void buildSelectionsFromBean(List<UIFieldBeanSelection> selections, EntityContext entityContext,
      List<OptionModel> selectOptions) {
    for (UIFieldBeanSelection selection : selections) {
      for (Map.Entry<String, ?> entry : entityContext.getBeansOfTypeWithBeanName(selection.value()).entrySet()) {
        OptionModel optionModel = OptionModel.of(entry.getKey());
        Object bean = entry.getValue();
        updateSelectedOptionModel(bean, null, selection.value(), optionModel, SelectHandler.bean.name());
        selectOptions.add(optionModel);
      }
    }
  }

  private static ObjectNode getTextBoxSelections(EntityUIMetaData entityUIMetaData, ObjectNode jsonTypeMetadata) {
    if (entityUIMetaData.getType().equals(UIFieldType.TextSelectBoxDynamic.name())) {
      if (!jsonTypeMetadata.has("textBoxSelections")) {
        jsonTypeMetadata.set("textBoxSelections", OBJECT_MAPPER.createArrayNode());
      }
      ArrayNode jsonArray = (ArrayNode) jsonTypeMetadata.get("textBoxSelections");
      ObjectNode subMeta = OBJECT_MAPPER.createObjectNode();
      jsonArray.add(subMeta);
      return subMeta;
    }
    return jsonTypeMetadata;
  }

  private static void updateSelectedOptionModel(Object target, Object requestedEntity, Class<?> sourceClassType,
      OptionModel optionModel, String selectHandler) {
    optionModel.json(jsonObject -> {
      jsonObject.put("REQ_TARGET", sourceClassType.getSimpleName() + "~~~" + selectHandler);
    });
    setDescription(target, sourceClassType, optionModel);

    String sp = null, icon = null, iconColor = null, descr = null;
    UIFieldSelectionParent selectionParent = target.getClass().getDeclaredAnnotation(UIFieldSelectionParent.class);
    if (selectionParent != null) {
      sp = selectionParent.value();
      icon = selectionParent.icon();
      iconColor = selectionParent.iconColor();
      descr = selectionParent.description();
    }

    if (target instanceof UIFieldSelectionParent.SelectionParent) {
      UIFieldSelectionParent.SelectionParent parent = (UIFieldSelectionParent.SelectionParent) target;
      sp = defaultIfEmpty(parent.getParentName(), sp);
      icon = defaultIfEmpty(parent.getParentIcon(), icon);
      iconColor = defaultIfEmpty(parent.getParentIconColor(), iconColor);
      descr = defaultIfEmpty(parent.getParentDescription(), descr);
    }

    if (sp != null) {
      optionModel.put("sp", new SelectionParent(sp, icon, iconColor, descr));
    }

    if (target instanceof SelectionWithDynamicParameterFields) {
      SelectionWithDynamicParameterFields.RequestDynamicParameter requestDynamicParameter =
          new SelectionWithDynamicParameterFields.RequestDynamicParameter(requestedEntity,
              fetchRequestWidgetType(requestedEntity, sourceClassType));

      optionModel.json(params -> {
        DynamicParameterFields dynamicParameterFields =
            ((SelectionWithDynamicParameterFields) target).getDynamicParameterFields(requestDynamicParameter);
        if (dynamicParameterFields != null) {
          try {
            params.put("dynamicParameter", new JSONObject().put("groupName", dynamicParameterFields.getGroupName())
                .put("borderColor", dynamicParameterFields.getBorderColor())
                .put("defaultValues", OBJECT_MAPPER.writeValueAsString(dynamicParameterFields))
                .put("class", dynamicParameterFields.getClass().getSimpleName())
                .put("holder", "dynamicParameterFieldsHolder"));
          } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
          }
        }
      });
    }
  }

  private static void setDescription(Object target, Class<?> sourceClassType, OptionModel optionModel) {
    List<Method> classDescriptionMethods =
        MethodUtils.getMethodsListWithAnnotation(sourceClassType, SelectDataSourceDescription.class);
    Method descriptionMethod = classDescriptionMethods.isEmpty() ? null : classDescriptionMethods.iterator().next();
    String entityTypeDescription;
    try {
      entityTypeDescription = descriptionMethod == null ? null : (String) descriptionMethod.invoke(target);
      if (entityTypeDescription != null) {
        optionModel.setDescription(Lang.getServerMessage(entityTypeDescription));
      }
    } catch (Exception ignore) {
    }
  }

  /**
   * Search for all OptionModels and find same key. Remove 'HasGetStatusValue if HasAggregateValueFromSeries exists
   */
  private static void filterResultOptions(OptionModel parent, Collection<OptionModel> options) {
    Map<String, List<OptionModel>> groupByKeyModels = options.stream().collect(groupingBy(OptionModel::getKey));
    for (Map.Entry<String, List<OptionModel>> entry : groupByKeyModels.entrySet()) {
      if (entry.getValue().size() > 1) {
        Set<String> reqTarget =
            entry.getValue().stream().map(v -> v.getJson().getString("REQ_TARGET").split("~~~")[0]).collect(toSet());
        if (reqTarget.contains(HasAggregateValueFromSeries.class.getSimpleName())) {
          entry.getValue().removeIf(
              e -> e.getJson().getString("REQ_TARGET").startsWith(HasGetStatusValue.class.getSimpleName()));
        }
      }
    }
    for (OptionModel optionModel : options) {
      if (optionModel.getChildren() != null) {
        filterResultOptions(optionModel, optionModel.getChildren());
      }
    }
    parent.getChildren().clear();
    parent.getChildren().addAll(groupByKeyModels.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
  }

  private static Method findMethodByName(Class<?> clz, String name,
      List<? extends Class<? extends Annotation>> annotationClasses) {
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

  @AllArgsConstructor
  private static class SelectionParent {

    private final String key;
    private final String icon;
    private final String iconColor;
    private final String description;

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
}

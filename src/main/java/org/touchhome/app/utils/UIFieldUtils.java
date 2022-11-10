package org.touchhome.app.utils;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.touchhome.app.manager.common.v1.UIInputBuilderImpl;
import org.touchhome.app.manager.common.v1.layout.UIDialogLayoutBuilderImpl;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.UIFieldMarkers;
import org.touchhome.app.model.entity.widget.UIFieldTimeSlider;
import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldExpand;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreGetDefault;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreParent;
import org.touchhome.bundle.api.ui.field.UIFieldInlineEntity;
import org.touchhome.bundle.api.ui.field.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldPort;
import org.touchhome.bundle.api.ui.field.UIFieldPosition;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldTableLayout;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIKeyValueField;
import org.touchhome.bundle.api.ui.field.action.ActionInputParameter;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.action.UIActionInput;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuUploadAction;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorBooleanMatch;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorMatch;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorSource;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorStatusMatch;
import org.touchhome.bundle.api.ui.field.image.UIFieldImage;
import org.touchhome.bundle.api.ui.field.image.UIFieldImageSrc;
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
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;

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
        uiFieldNameToGetters.values().stream().map(UIFieldMethodContext::getMethodName).collect(toSet());

    FieldUtils.getFieldsListWithAnnotation(instance.getClass(), UIField.class).forEach(field -> {
      if (!processedMethods.contains(field.getName())) { // skip if already managed by methods
        String capitalizeMethodName = StringUtils.capitalize(field.getName());
        List<Method> fieldGetterMethods =
            fieldNameToGetters.getOrDefault("get" + capitalizeMethodName, new ArrayList<>());
        fieldGetterMethods.addAll(fieldNameToGetters.getOrDefault("is" + capitalizeMethodName, Collections.emptyList()));
        generateUIField(instance, entityUIMetaDataSet, new UIFieldFieldContext(field, fieldGetterMethods), entityContext);
      }
    });

    return new ArrayList<>(entityUIMetaDataSet);
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

    ObjectMapper mapper = CommonUtils.OBJECT_MAPPER;
    ObjectNode jsonTypeMetadata = mapper.createObjectNode();

    if (uiField.type().equals(UIFieldType.AutoDetect)) {
      if (type.isEnum() || uiFieldContext.isAnnotationPresent(UIFieldTreeNodeSelection.class) ||
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
        if (uiFieldContext.isAnnotationPresent(UIFieldTreeNodeSelection.class) &&
            uiFieldContext.getDeclaredAnnotation(UIFieldTreeNodeSelection.class).allowInputRawText()) {
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
          extractSetEntityType(instance, uiFieldContext, entityUIMetaData, (ParameterizedType) genericType,
              jsonTypeMetadata);
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
    if (!uiField.visible()) {
      jsonTypeMetadata.put("hideFromUI", true);
    }
    if (StringUtils.isNotEmpty(uiField.bg())) {
      jsonTypeMetadata.put("bg", uiField.bg());
    }

    UIFieldIconPicker uiFieldIconPicker = uiFieldContext.getDeclaredAnnotation(UIFieldIconPicker.class);
    if (uiFieldIconPicker != null) {
      entityUIMetaData.setType("IconPicker");
      jsonTypeMetadata.put("allowEmptyIcon", uiFieldIconPicker.allowEmptyIcon());
      jsonTypeMetadata.put("allowSize", uiFieldIconPicker.allowSize());
      jsonTypeMetadata.put("allowSpin", uiFieldIconPicker.allowSpin());
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

    UIFieldSelectionUtil.handleFieldSelections(uiFieldContext, entityContext, entityUIMetaData, jsonTypeMetadata);

    UIFieldMarkers uiFieldMarkers = uiFieldContext.getDeclaredAnnotation(UIFieldMarkers.class);
    if (uiFieldMarkers != null) {
      entityUIMetaData.setType("Markers");
      jsonTypeMetadata.set("markerOP", OBJECT_MAPPER.valueToTree(uiFieldMarkers.value()));
    }

    UIFieldLayout uiFieldLayout = uiFieldContext.getDeclaredAnnotation(UIFieldLayout.class);
    if (uiFieldLayout != null) {
      entityUIMetaData.setType("Layout");
      jsonTypeMetadata.put("layoutRows", uiFieldLayout.rows());
      jsonTypeMetadata.put("layoutColumns", uiFieldLayout.columns());
      jsonTypeMetadata.set("layoutOptions", OBJECT_MAPPER.valueToTree(uiFieldLayout.options()));
    }

    UIFieldShowOnCondition uiFieldShowOnCondition = uiFieldContext.getDeclaredAnnotation(UIFieldShowOnCondition.class);
    if (uiFieldShowOnCondition != null) {
      jsonTypeMetadata.put("showCondition", uiFieldShowOnCondition.value());
    }

    UIFieldGroup uiFieldGroup = uiFieldContext.getDeclaredAnnotation(UIFieldGroup.class);
    if (uiFieldGroup != null) {
      jsonTypeMetadata.put("group", uiFieldGroup.value());
      if (uiFieldGroup.order() > 0) {
        jsonTypeMetadata.put("groupOrder", uiFieldGroup.order());
      }
      if (StringUtils.isNotEmpty(uiFieldGroup.borderColor())) {
        jsonTypeMetadata.put("borderColor", uiFieldGroup.borderColor());
      }
    }

    UIEditReloadWidget uiEditReloadWidget = uiFieldContext.getDeclaredAnnotation(UIEditReloadWidget.class);
    if (uiEditReloadWidget != null) {
      jsonTypeMetadata.put("reloadOnUpdate", true);
    }

    Pattern pattern = uiFieldContext.getDeclaredAnnotation(Pattern.class);
    if (pattern != null) {
      jsonTypeMetadata.put("regexp", pattern.regexp());
      jsonTypeMetadata.put("regexpMsg", pattern.message());
    }

    UIFieldTimeSlider uiFieldTimeSlider = uiFieldContext.getDeclaredAnnotation(UIFieldTimeSlider.class);
    if (uiFieldTimeSlider != null) {
      entityUIMetaData.setType("TimeSlider");
    }

    UIFieldProgress uiFieldProgress = uiFieldContext.getDeclaredAnnotation(UIFieldProgress.class);
    if (uiFieldProgress != null) {
      entityUIMetaData.setType("Progress");
      jsonTypeMetadata.put("color", uiFieldProgress.color());
      jsonTypeMetadata.put("bgColor", uiFieldProgress.fillColor());
      jsonTypeMetadata.set("colorChange",
          OBJECT_MAPPER.valueToTree(Arrays.stream(uiFieldProgress.colorChange()).collect(
              Collectors.toMap(UIFieldProgress.UIFieldProgressColorChange::color,
                  UIFieldProgress.UIFieldProgressColorChange::whenMoreThan))));
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
      jsonTypeMetadata.set("actionButtons", OBJECT_MAPPER.valueToTree(actionButtons));
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
      jsonTypeMetadata.set("valueColor", OBJECT_MAPPER.valueToTree(colors));
    }

    UIFieldColorStatusMatch uiFieldColorStatusMatch = uiFieldContext.getDeclaredAnnotation(UIFieldColorStatusMatch.class);
    if (uiFieldColorStatusMatch != null) {
      ObjectNode colors = OBJECT_MAPPER.createObjectNode();
      colors.put(Status.OFFLINE.name(), uiFieldColorStatusMatch.offline());
      colors.put(Status.ONLINE.name(), uiFieldColorStatusMatch.online());
      colors.put(Status.UNKNOWN.name(), uiFieldColorStatusMatch.unknown());
      colors.put(Status.ERROR.name(), uiFieldColorStatusMatch.error());
      colors.put(Status.REQUIRE_AUTH.name(), uiFieldColorStatusMatch.requireAuth());
      colors.put(Status.DONE.name(), uiFieldColorStatusMatch.done());
      colors.put(Status.NOT_SUPPORTED.name(), uiFieldColorStatusMatch.notSupported());
      colors.put(Status.RUNNING.name(), uiFieldColorStatusMatch.running());
      colors.put(Status.WAITING.name(), uiFieldColorStatusMatch.waiting());
      jsonTypeMetadata.set("valueColor", colors);
      jsonTypeMetadata.put("valueColorPrefix", uiFieldColorStatusMatch.handlePrefixes());
    }

    UIFieldColorBooleanMatch uiFieldColorBooleanMatch = uiFieldContext.getDeclaredAnnotation(UIFieldColorBooleanMatch.class);
    if (uiFieldColorBooleanMatch != null) {
      JSONObject colors = new JSONObject();
      colors.put("true", uiFieldColorBooleanMatch.False());
      colors.put("false", uiFieldColorBooleanMatch.True());
      jsonTypeMetadata.put("valueColor", OBJECT_MAPPER.valueToTree(colors));
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

    UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty = uiFieldContext.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
    if (uiFieldSelectValueOnEmpty != null) {
      ObjectNode selectValueOnEmpty = OBJECT_MAPPER.createObjectNode();
      selectValueOnEmpty.put("color", uiFieldSelectValueOnEmpty.color());
      selectValueOnEmpty.put("label", uiFieldSelectValueOnEmpty.label());
      selectValueOnEmpty.put("icon", uiFieldSelectValueOnEmpty.icon());
      jsonTypeMetadata.put("selectValueOnEmpty", selectValueOnEmpty);
    }

    UIFieldSelectNoValue uiFieldSelectNoValue = uiFieldContext.getDeclaredAnnotation(UIFieldSelectNoValue.class);
    if (uiFieldSelectNoValue != null) {
      jsonTypeMetadata.put("optionsNotFound", uiFieldSelectNoValue.value());
    }

    if (entityUIMetaData.getType().equals(String.class.getSimpleName())) {
      UIKeyValueField uiKeyValueField = uiFieldContext.getDeclaredAnnotation(UIKeyValueField.class);
      if (uiKeyValueField != null) {
        jsonTypeMetadata.put("maxSize", uiKeyValueField.maxSize());
        jsonTypeMetadata.set("keyType", OBJECT_MAPPER.valueToTree(uiKeyValueField.keyType()));
        jsonTypeMetadata.set("valueType", OBJECT_MAPPER.valueToTree(uiKeyValueField.valueType()));
        jsonTypeMetadata.put("defaultKey", uiKeyValueField.defaultKey());
        jsonTypeMetadata.put("defaultValue", uiKeyValueField.defaultValue());
        jsonTypeMetadata.put("keyFormat", uiKeyValueField.keyFormat());
        jsonTypeMetadata.put("valueFormat", uiKeyValueField.valueFormat());
        jsonTypeMetadata.put("keyValueType", uiKeyValueField.keyValueType().name());
        entityUIMetaData.setType("KeyValue");
      }
    }

    UIFieldUpdateFontSize uiFieldUpdateFontSize = uiFieldContext.getDeclaredAnnotation(UIFieldUpdateFontSize.class);
    if (uiFieldUpdateFontSize != null) {
      jsonTypeMetadata.put("fsMin", uiFieldUpdateFontSize.min());
      jsonTypeMetadata.put("fsMax", uiFieldUpdateFontSize.max());
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
      jsonTypeMetadata.set("editorType", OBJECT_MAPPER.valueToTree(uiFieldCodeEditor.editorType()));
      entityUIMetaData.setType("CodeEditor");
    }

    UIFieldInlineEntityWidth uiFieldInlineEntityWidth = uiFieldContext.getDeclaredAnnotation(UIFieldInlineEntityWidth.class);
    if (uiFieldInlineEntityWidth != null) {
      jsonTypeMetadata.put("inlineEditWidth", uiFieldInlineEntityWidth.editWidth());
      jsonTypeMetadata.put("inlineViewWidth", uiFieldInlineEntityWidth.viewWidth());
    }

    UIFieldInlineEntity uiFieldInline = uiFieldContext.getDeclaredAnnotation(UIFieldInlineEntity.class);
    if (uiFieldInline != null) {

      if (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom(type)) {
        Type inlineType =
            extractSetEntityType(instance, uiFieldContext, entityUIMetaData, (ParameterizedType) genericType,
                jsonTypeMetadata);
        // fill inlineType fields!
        Object childClassInstance = CommonUtils.newInstance((Class) inlineType);

        jsonTypeMetadata.set("inlineTypeFields",
            OBJECT_MAPPER.valueToTree(UIFieldUtils.fillEntityUIMetadataList(childClassInstance, new HashSet<>(), entityContext)));
        entityUIMetaData.setStyle("height: 100%; padding: 0; background: " + uiFieldInline.bg() + ";");
        jsonTypeMetadata.put("fw", true);
        jsonTypeMetadata.put("addRow", uiFieldInline.addRow());
        jsonTypeMetadata.put("showInGeneral", true);
      } else {
        throw new IllegalStateException(
            "Unable to annotate field " + uiFieldContext.getSourceName() + " with UIFieldType.InlineEntity");
      }
    }

    if (uiField.showInContextMenu() && entityUIMetaData.getType().equals(Boolean.class.getSimpleName())) {
      entityUIMetaData.setShowInContextMenu(true);
    }
    if (jsonTypeMetadata.size() != 0) {
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

  private static Type extractSetEntityType(Object instance, UIFieldContext uiFieldContext, EntityUIMetaData entityUIMetaData,
      ParameterizedType genericType, ObjectNode jsonTypeMetadata) {
    Type typeArgument = genericType.getActualTypeArguments()[0];
    if (!trySetListType(typeArgument, entityUIMetaData, uiFieldContext, jsonTypeMetadata)) {
      typeArgument = findClassGenericClass(typeArgument, instance);
      if (!trySetListType(typeArgument, entityUIMetaData, uiFieldContext, jsonTypeMetadata)) {
        entityUIMetaData.setType(UIFieldType.Chips.name());
        jsonTypeMetadata.put("type", ((Class) typeArgument).getSimpleName());
      } else {
        typeArgument = null;
      }
    }
    return typeArgument;
  }

  private static boolean trySetListType(Type argument, EntityUIMetaData entityUIMetaData, UIFieldContext uiFieldContext, ObjectNode jsonTypeMetadata) {
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
}

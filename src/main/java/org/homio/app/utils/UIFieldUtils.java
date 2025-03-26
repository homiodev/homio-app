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
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIconPicker;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.UIFieldInlineEditConfirm;
import org.homio.api.ui.field.UIFieldKeyValue;
import org.homio.api.ui.field.UIFieldLayout;
import org.homio.api.ui.field.UIFieldLinkToEntity;
import org.homio.api.ui.field.UIFieldNoReadDefaultValue;
import org.homio.api.ui.field.UIFieldNumber;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldPosition;
import org.homio.api.ui.field.UIFieldProgress;
import org.homio.api.ui.field.UIFieldProgress.UIFieldProgressColorChange;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldStringTemplate;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldTableLayout;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.UIContextMenuUploadAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.color.UIFieldColorBooleanMatch;
import org.homio.api.ui.field.color.UIFieldColorMatch;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.color.UIFieldColorStatusMatch;
import org.homio.api.ui.field.condition.UIFieldDisableCreateTab;
import org.homio.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.image.UIFieldImage;
import org.homio.api.ui.field.image.UIFieldImageSrc;
import org.homio.api.ui.field.inline.UIFieldInlineEditEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityEditWidth;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.inline.UIFieldInlineGroup;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldDevicePortSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldEntityTypeSelection;
import org.homio.api.ui.field.selection.UIFieldSelectConfig;
import org.homio.api.ui.field.selection.UIFieldStaticSelection;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SecureString;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.homio.app.model.UIFieldClickToEdit;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldFunction;
import org.homio.app.model.entity.widget.UIFieldMarkers;
import org.homio.app.model.entity.widget.UIFieldOptionColor;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.UIFieldOptionVerticalAlign;
import org.homio.app.model.entity.widget.UIFieldPadding;
import org.homio.app.model.entity.widget.UIFieldTimeSlider;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.model.var.UIFieldVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.core.annotation.AnnotationUtils;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
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
    Object instance = CommonUtils.newInstance(entityClassByType);
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
    UISidebarMenu uiSidebarMenu = AnnotationUtils.findAnnotation(entityClass, UISidebarMenu.class);
    if (uiSidebarMenu == null) {
      throw new IllegalArgumentException("Unable to create link for field: " + name + " and class: " + entityClass.getSimpleName());
    }
    String href = StringUtils.defaultIfEmpty(uiSidebarMenu.overridePath(), entityClass.getSimpleName());
    return uiSidebarMenu.parent().name().toLowerCase() + "/" + href;
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
      putIfTrue(jsonTypeMetadata, "simple", fieldIconPicker.simple());
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

    var fieldLink = fieldContext.getDeclaredAnnotation(UIFieldLinkToEntity.class);
    if (fieldLink != null) {
      entityUIMetaData.setNavLink(getClassEntityNavLink(field.name(), fieldLink.value()));
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

    if (BaseEntity.class.isAssignableFrom(type) && type.getDeclaredAnnotation(UISidebarMenu.class) != null) {
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
        || isMatch(fieldContext, UIFieldStaticSelection.class, UIFieldStaticSelection::rawInput)
        || isMatch(fieldContext, UIFieldTreeNodeSelection.class, UIFieldTreeNodeSelection::rawInput)
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

  private static int getSelectAnnotationCount(UIFieldContext fieldContext) {
    return fieldContext.getDeclaredAnnotationsByType(UIFieldTreeNodeSelection.class).size()
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

package org.homio.app.builder.ui.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.v1.UIEntityBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.homio.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.app.builder.ui.UICheckboxItemBuilderImpl;
import org.homio.app.builder.ui.UIDialogInputEntity;
import org.homio.app.builder.ui.UIInfoItemBuilderImpl;
import org.homio.app.builder.ui.UIItemType;
import org.homio.app.builder.ui.UISelectBoxItemBuilderImpl;
import org.homio.app.builder.ui.UISliderItemBuilderImpl;
import org.homio.app.builder.ui.UITextInputItemBuilderImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.homio.api.ui.field.action.ActionInputParameter.NAME_PATTERN;

@Getter
@Accessors(chain = true)
public class UIDialogLayoutBuilderImpl implements UIDialogLayoutBuilder {

  @Getter
  private final String entityID;
  private final Map<String, UIEntityBuilder> inputBuilders = new LinkedHashMap<>();
  @Setter
  private Integer width;
  private String title;
  private String icon;
  private String iconColor;

  // this order only for flex
  @Setter
  private int order;

  @JsonIgnore
  private Map<String, String> styleMap;

  public UIDialogLayoutBuilderImpl(String entityID) {
    this.entityID = entityID + "_dialog";
    this.title = entityID;
  }

  @Override
  public String toString() {
    return "%s{%s:%d}".formatted(getClass().getSimpleName(), getEntityID(), getOrder());
  }

  @Override
  public UIDialogLayoutBuilder setTitle(@NotNull String title, Icon icon) {
    this.title = title;
    this.icon = Icon.iconOrDefault(icon, null);
    this.iconColor = Icon.colorOrDefault(icon, null);
    return this;
  }

  @Override
  public DialogEntity<UIFlexLayoutBuilder> addFlex(@NotNull String name) {
    return addEntity(name, new UIFlexLayoutBuilderImpl(name, nextOrder()));
  }

  @Override
  public String getStyle() {
    return styleMap == null ? null :
      styleMap.entrySet().stream()
        .map(e -> e.getKey() + ":" + e.getValue() + ";")
        .collect(Collectors.joining());
  }

  @Override
  public UIDialogLayoutBuilder appendStyle(@NotNull String style, @NotNull String value) {
    if (this.styleMap == null) {
      this.styleMap = new HashMap<>(2);
    }
    value = value.endsWith(";") ? value.substring(0, value.length() - 1) : value;
    this.styleMap.put(style, value);
    return this;
  }

  public DialogEntity<UITextInputItemBuilder> addInput(@NotNull String name, String defaultValue, UITextInputItemBuilder.InputType inputType,
                                                       boolean required) {
    return addEntity(name, new UITextInputItemBuilderImpl(name, nextOrder(), defaultValue, inputType).setRequired(required));
  }

  public <T extends UIEntityBuilder> DialogEntity<T> addEntity(String key, T entityBuilder) {
    if (!NAME_PATTERN.matcher(entityBuilder.getEntityID()).matches()) {
      throw new IllegalArgumentException("Wrong name pattern for: " + entityBuilder.getEntityID());
    }
    inputBuilders.put(key, entityBuilder);
    return new DialogEntity<>() {
      @Override
      public UIDialogLayoutBuilder up() {
        return UIDialogLayoutBuilderImpl.this;
      }

      @Override
      public UIDialogLayoutBuilder edit(Consumer<T> editHandler) {
        editHandler.accept(entityBuilder);
        return UIDialogLayoutBuilderImpl.this;
      }
    };
  }

  public int nextOrder() {
    return inputBuilders.size() + 1;
  }

  public void addInput(UIActionInput input) {
    switch (input.type()) {
      case select -> {
        UISelectBoxItemBuilderImpl selectBox = new UISelectBoxItemBuilderImpl(input.name(), nextOrder())
          .setOptions(OptionModel.list(input.values()));
        addEntity(input.name(), selectBox.setValue(input.value()));
      }
      case text -> addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Text, input.required());
      case json -> addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.JSON, input.required());
      case textarea ->
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.TextArea, input.required());
      case password ->
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Password, input.required());
      case number -> addEntity(input.name(),
        new UISliderItemBuilderImpl(input.name(), nextOrder(), Float.parseFloat(input.value()), (float) input.min(), (float) input.max()))
        .edit(sliderBuilder -> sliderBuilder
          .setSliderType(UISliderItemBuilder.SliderType.Input)
          .setRequired(input.required()));
      case info ->
        addEntity(input.value(), new UIInfoItemBuilderImpl("txt_" + Math.abs(input.value().hashCode()), nextOrder(), input.value(), InfoType.Text));
      case bool ->
        addEntity(input.name(), new UICheckboxItemBuilderImpl(input.name(), nextOrder(), Boolean.parseBoolean(input.value())));
    }
  }

  public UIInputEntity buildEntity() {
    List<UIInputEntity> entities =
      getInputBuilders().values().stream()
        .map(UIEntityBuilder::buildEntity)
        .sorted(Comparator.comparingInt(UIInputEntity::getOrder))
        .collect(Collectors.toList());
    return new UIDialogInputEntity(entityID, this.order, UIItemType.Dialog.name(), title, icon, iconColor, getStyle(), width, entities);
  }
}

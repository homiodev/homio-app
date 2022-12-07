package org.touchhome.app.manager.common.v1.layout;

import static org.touchhome.bundle.api.ui.field.action.ActionInputParameter.NAME_PATTERN;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.manager.common.v1.UIDialogInputEntity;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.app.manager.common.v1.item.UICheckboxItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UIInfoItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UISliderItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UITextInputItemBuilderImpl;
import org.touchhome.bundle.api.ui.field.action.UIActionInput;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.touchhome.bundle.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.UITextInputItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;

@Getter
@Accessors(chain = true)
public class UIDialogLayoutBuilderImpl implements UIDialogLayoutBuilder {

  @Getter
  private final String entityID;

  private final Integer width;
  private final Map<String, UIEntityBuilder> inputBuilders = new LinkedHashMap<>();

  private String title;
  private String icon;
  private String iconColor;

  // this order only for flex
  @Setter
  private int order;

  @JsonIgnore
  private Map<String, String> styleMap;

  public UIDialogLayoutBuilderImpl(String entityID, Integer width) {
    this.entityID = entityID + "_dialog";
    this.title = entityID + "_title";
    this.width = width;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + getEntityID() + ":" + getOrder() + "}";
  }

  @Override
  public UIDialogLayoutBuilder setTitle(String title, String icon, String iconColor) {
    if (title != null) {
      this.title = title;
    }
    this.icon = icon;
    this.iconColor = iconColor;
    return this;
  }

  @Override
  public DialogEntity<UIFlexLayoutBuilder> addFlex(@NotNull String name) {
    return addEntity(name, new UIFlexLayoutBuilderImpl(name, nextOrder()));
  }

  @Override
  public String getStyle() {
    return styleMap == null ? null : styleMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue() + ";")
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

  public DialogEntity<UITextInputItemBuilder> addInput(@NotNull String name, String defaultValue,
      UITextInputItemBuilder.InputType inputType,
      boolean required) {
    return addEntity(name, new UITextInputItemBuilderImpl(name, nextOrder(), defaultValue, inputType)
        .setRequired(required));
  }

  public <T extends UIEntityBuilder> DialogEntity<T> addEntity(String key, T entityBuilder) {
    if (!NAME_PATTERN.matcher(entityBuilder.getEntityID()).matches()) {
      throw new IllegalArgumentException("Wrong name pattern for: " + entityBuilder.getEntityID());
    }
    inputBuilders.put(key, entityBuilder);
    return new DialogEntity<T>() {
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
      case text:
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Text, input.required());
        break;
      case json:
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.JSON, input.required());
        break;
      case textarea:
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.TextArea, input.required());
        break;
      case password:
        addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Password, input.required());
        break;
      case number:
        addEntity(input.name(), new UISliderItemBuilderImpl(input.name(), nextOrder(), null,
            Float.parseFloat(input.value()), (float) input.min(), (float) input.max())).
            edit(sliderBuilder ->
                sliderBuilder.setSliderType(UISliderItemBuilder.SliderType.Input).setRequired(input.required()));
        break;
      case info:
        addEntity(input.value(), new UIInfoItemBuilderImpl("txt_" + input.value().hashCode(),
            nextOrder(), input.value(), InfoType.Text));
        break;
      case bool:
        addEntity(input.name(), new UICheckboxItemBuilderImpl(input.name(), nextOrder(), null, Boolean.parseBoolean(input.value())));
        break;
    }
  }

  public UIInputEntity buildEntity() {
    List<UIInputEntity> entities = getInputBuilders().values().stream().map(UIEntityBuilder::buildEntity)
                                                     .sorted(Comparator.comparingInt(UIInputEntity::getOrder)).collect(Collectors.toList());
    return new UIDialogInputEntity(entityID, this.order, UIItemType.Dialog.name(), title, icon, iconColor, getStyle(), width,
        entities);
  }
}

package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder;

@Getter
@Accessors(chain = true)
public class UITextInputItemBuilderImpl
  extends UIBaseEntityItemBuilderImpl<UITextInputItemBuilder, String>
  implements UITextInputItemBuilder {

  private final InputType inputType;
  @Setter
  private boolean requireApply;

  public UITextInputItemBuilderImpl(String entityID, int order, String defaultValue, InputType inputType) {
    super(UIItemType.Input, entityID, order);
    setValue(defaultValue);
    this.inputType = inputType;
  }
}

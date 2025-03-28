package org.homio.app.builder.ui;

import lombok.Getter;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UICheckboxItemBuilder;

@Getter
public class UICheckboxItemBuilderImpl
  extends UIBaseEntityItemBuilderImpl<UICheckboxItemBuilder, Boolean>
  implements UICheckboxItemBuilder, UIInputEntity {

  public UICheckboxItemBuilderImpl(String entityID, int order, boolean value) {
    super(UIItemType.Checkbox, entityID, order);
    setValue(value);
  }
}

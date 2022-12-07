package org.touchhome.app.manager.common.v1.item;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;

@Getter
public class UIMultiButtonItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIMultiButtonItemBuilder, String>
    implements UIMultiButtonItemBuilder {

  private final List<ExtraButton> buttons = new ArrayList<>();

  public UIMultiButtonItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler) {
    super(UIItemType.MultiButton, entityID, order, actionHandler);
  }

  @Override
  public UIMultiButtonItemBuilderImpl addButton(String title) {
    buttons.add(new ExtraButton(title, null, null));
    return this;
  }

  @Override
  public UIMultiButtonItemBuilderImpl addButton(String title, String icon, String iconColor) {
    buttons.add(new ExtraButton(title, iconColor, icon));
    return this;
  }

  @Override
  public UIMultiButtonItemBuilderImpl setActive(String activeButton) {
    setValue(activeButton);
    return this;
  }

  public List<ExtraButton> getButtons() {
    ArrayList<ExtraButton> list = new ArrayList<>();
    list.add(new ExtraButton(getEntityID(), getIconColor(), getIcon()));
    list.addAll(buttons);
    return list;
  }

  @Override
  public String getIcon() {
    return null;
  }

  @Override
  public String getIconColor() {
    return null;
  }

  @Getter
  @AllArgsConstructor
  public static class ExtraButton {

    String name;
    String iconColor;
    String icon;
  }
}

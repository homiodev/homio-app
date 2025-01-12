package org.homio.app.builder.ui;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.model.Icon;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class UIMultiButtonItemBuilderImpl
  extends UIBaseEntityItemBuilderImpl<UIMultiButtonItemBuilder, String>
  implements UIMultiButtonItemBuilder {

  private final List<ExtraButton> buttons = new ArrayList<>();

  public UIMultiButtonItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler) {
    super(UIItemType.MultiButton, entityID, order, actionHandler);
  }

  @Override
  public @NotNull UIMultiButtonItemBuilder addButton(@NotNull String key, @Nullable String title, @Nullable Icon icon) {
    if (icon == null) {
      buttons.add(new ExtraButton(key, Objects.toString(title, key), null, null));
    } else {
      buttons.add(new ExtraButton(key, title, icon.getColor(), icon.getIcon()));
    }
    return this;
  }

  @Override
  public @NotNull UIMultiButtonItemBuilderImpl setActive(@NotNull String activeButton) {
    setValue(activeButton);
    return this;
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

    String key;
    String title;
    String color;
    String icon;
  }
}

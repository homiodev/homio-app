package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.model.Icon;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Getter
@Setter
@Accessors(chain = true)
public class UIButtonItemBuilderImpl
  extends UIBaseEntityItemBuilderImpl<UIButtonItemBuilder, String>
  implements UIButtonItemBuilder {

  private String[] fireActionsBeforeChange;
  private String actionReference;
  private String actionReferenceV2;
  private JSONObject metadata;
  private String confirmMessage;
  private String confirmMessageDialogColor;
  private String confirmMessageDialogTitle;
  private Icon confirmMessageDialogIcon;

  private UIDialogLayoutBuilder dialogEntityBuilder;

  private UIStickyDialogItemBuilder stickyDialogBuilder;
  private boolean primary = true;
  private int height = 32;

  public UIButtonItemBuilderImpl(@NotNull UIItemType uiItemType, @NotNull String entityID, @Nullable Icon icon, int order) {
    super(uiItemType, entityID, order);
    if (icon == null || StringUtils.isEmpty(icon.getIcon())) {
      setValue(entityID);
    }
    setText(entityID.isEmpty() ? "" : entityID);
    setIcon(icon);
  }

  public UIButtonItemBuilderImpl setText(@Nullable String text) {
    this.setValue(text);
    return this;
  }

  public UIInputEntity getDialogReference() {
    return dialogEntityBuilder == null
      ? (stickyDialogBuilder == null ? null : stickyDialogBuilder.buildEntity())
      : dialogEntityBuilder.buildEntity();
  }

  public boolean isSticky() {
    return this.stickyDialogBuilder != null;
  }
}

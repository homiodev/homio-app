package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;

@Getter
public class UIButtonItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIButtonItemBuilder, String>
    implements UIButtonItemBuilder {

  private String[] fireActionsBeforeChange;
  private String actionReference;
  private JSONObject metadata;

  private UIDialogLayoutBuilder dialogEntityBuilder;
  @Getter
  private UIStickyDialogItemBuilder stickyDialogBuilder;
  private boolean primary = true;

  public UIButtonItemBuilderImpl(UIItemType uiItemType, String entityID, String icon, String iconColor, int order,
      UIActionHandler actionHandler) {
    super(uiItemType, entityID, order, actionHandler);
    setValue(entityID);
    setIcon(icon, iconColor);
  }

  public UIButtonItemBuilderImpl setMetadata(JSONObject metadata) {
    this.metadata = metadata;
    return this;
  }

  public UIButtonItemBuilderImpl setDialogEntityBuilder(UIDialogLayoutBuilder dialogEntityBuilder) {
    this.dialogEntityBuilder = dialogEntityBuilder;
    return this;
  }

  public UIButtonItemBuilderImpl setStickyDialogEntityBuilder(UIStickyDialogItemBuilder stickyDialogBuilder) {
    this.stickyDialogBuilder = stickyDialogBuilder;
    return this;
  }

  public UIButtonItemBuilderImpl setText(@Nullable String text) {
    this.setValue(text);
    return this;
  }

  public UIButtonItemBuilderImpl setFireActionsBeforeChange(String[] actions) {
    this.fireActionsBeforeChange = actions;
    return this;
  }

  public UIButtonItemBuilderImpl setActionReference(String actionReference) {
    this.actionReference = actionReference;
    return this;
  }

  @Override
  public UIButtonItemBuilderImpl setPrimary(boolean primary) {
    this.primary = primary;
    return this;
  }

  public UIInputEntity getDialogReference() {
    return dialogEntityBuilder == null ? (stickyDialogBuilder == null ? null : stickyDialogBuilder.buildEntity()) :
        dialogEntityBuilder.buildEntity();
  }

  public boolean isSticky() {
    return this.stickyDialogBuilder != null;
  }
}

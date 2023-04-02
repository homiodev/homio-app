package org.homio.app.builder.ui;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.action.v1.UIInputEntity;
import org.homio.bundle.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Getter
public class UIButtonItemBuilderImpl
    extends UIBaseEntityItemBuilderImpl<UIButtonItemBuilder, String>
    implements UIButtonItemBuilder {

    private String[] fireActionsBeforeChange;
    private String actionReference;
    private JSONObject metadata;

    private UIDialogLayoutBuilder dialogEntityBuilder;
    @Getter private UIStickyDialogItemBuilder stickyDialogBuilder;
    private boolean primary = true;

    public UIButtonItemBuilderImpl(UIItemType uiItemType, String entityID, String icon, String iconColor, int order, UIActionHandler actionHandler) {
        super(uiItemType, entityID, order, actionHandler);
        if (StringUtils.isEmpty(icon)) {
            setValue(entityID);
        }
        setText(entityID);
        setIcon(icon, iconColor);
    }

    public UIButtonItemBuilderImpl setMetadata(JSONObject metadata) {
        this.metadata = metadata;
        return this;
    }

    public UIButtonItemBuilderImpl setDialogEntityBuilder(
        UIDialogLayoutBuilder dialogEntityBuilder) {
        this.dialogEntityBuilder = dialogEntityBuilder;
        return this;
    }

    public UIButtonItemBuilderImpl setStickyDialogEntityBuilder(
        UIStickyDialogItemBuilder stickyDialogBuilder) {
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
        return dialogEntityBuilder == null
            ? (stickyDialogBuilder == null ? null : stickyDialogBuilder.buildEntity())
            : dialogEntityBuilder.buildEntity();
    }

    public boolean isSticky() {
        return this.stickyDialogBuilder != null;
    }
}

package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.action.v1.UIInputEntity;
import org.homio.bundle.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
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
    private JSONObject metadata;

    private UIDialogLayoutBuilder dialogEntityBuilder;

    private UIStickyDialogItemBuilder stickyDialogBuilder;
    private boolean primary = true;
    private int height = 32;

    public UIButtonItemBuilderImpl(UIItemType uiItemType, String entityID, String icon, String iconColor, int order, UIActionHandler actionHandler) {
        super(uiItemType, entityID, order, actionHandler);
        if (StringUtils.isEmpty(icon)) {
            setValue(entityID);
        }
        setText("CONTEXT.ACTION." + entityID);
        setIcon(icon, iconColor);
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

package org.touchhome.app.manager.common.v1.item;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;

public class UIButtonItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIButtonItemBuilder, String>
        implements UIButtonItemBuilder {

    private String[] fireActionsBeforeChange;
    private String actionReference;

    private UIDialogLayoutBuilder dialogEntityBuilder;
    @Getter
    private UIStickyDialogItemBuilder stickyDialogBuilder;

    public UIButtonItemBuilderImpl(UIItemType uiItemType, String entityID, String icon, String iconColor, int order, UIActionHandler actionHandler) {
        super(uiItemType, entityID, order, actionHandler);
        setValue(entityID);
        setIcon(icon, iconColor);
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
    public UIInputEntity buildEntity() {
        return new UIButtonEntity(getActionHandler(), getEntityID(), getValue(), getTitle(), getItemType(), getOrder(),
                dialogEntityBuilder == null ? (stickyDialogBuilder == null ? null : stickyDialogBuilder.buildEntity()) :
                        dialogEntityBuilder.buildEntity(),
                this.stickyDialogBuilder != null,
                this.actionReference, this.fireActionsBeforeChange, getIcon(),
                getIconColor(), getColor(), isDisabled());
    }

    @Getter
    @RequiredArgsConstructor
    public static class UIButtonEntity implements UIInputEntityActionHandler {
        @JsonIgnore
        private final UIActionHandler actionHandler;

        private final String entityID;
        private final String text;
        private final String title;
        private final String itemType;
        private final int order;

        private final UIInputEntity dialogReference;
        private final boolean sticky;
        private final String actionReference;
        private final String[] fireActionsBeforeChange;

        private final String icon;
        private final String iconColor;
        private final String color;
        private final boolean disabled;

        @Override
        public String toString() {
            return "UIButtonEntity{" + entityID + "," + order + "}";
        }
    }
}

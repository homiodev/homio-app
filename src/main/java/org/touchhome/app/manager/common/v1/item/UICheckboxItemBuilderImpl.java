package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.item.UICheckboxItemBuilder;

@Getter
public class UICheckboxItemBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UICheckboxItemBuilder, Boolean>
        implements UICheckboxItemBuilder, UIInputEntity {

    public UICheckboxItemBuilderImpl(
            String entityID, int order, UIActionHandler actionHandler, boolean value) {
        super(UIItemType.Checkbox, entityID, order, actionHandler);
        setValue(value);
    }
}

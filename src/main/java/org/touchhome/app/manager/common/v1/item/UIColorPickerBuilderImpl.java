package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIColorPickerItemBuilder;

@Getter
public class UIColorPickerBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UIColorPickerItemBuilder, String>
        implements UIColorPickerItemBuilder {

    private ColorType colorType;

    public UIColorPickerBuilderImpl(
            String entityID, int order, String value, UIActionHandler actionHandler) {
        super(UIItemType.ColorPicker, entityID, order, actionHandler);
        setValue(value);
    }

    @Override
    public UIColorPickerItemBuilder setColorType(ColorType colorType) {
        this.colorType = colorType;
        return this;
    }
}

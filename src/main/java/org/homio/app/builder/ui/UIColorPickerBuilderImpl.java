package org.homio.app.builder.ui;

import lombok.Getter;
import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.action.v1.item.UIColorPickerItemBuilder;

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

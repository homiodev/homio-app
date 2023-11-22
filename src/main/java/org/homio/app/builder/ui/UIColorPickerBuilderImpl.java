package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UIColorPickerItemBuilder;

@Getter
@Accessors(chain = true)
public class UIColorPickerBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UIColorPickerItemBuilder, String>
        implements UIColorPickerItemBuilder {

    @Setter
    private ColorType colorType;

    public UIColorPickerBuilderImpl(String entityID, int order, String value, UIActionHandler actionHandler) {
        super(UIItemType.ColorPicker, entityID, order, actionHandler);
        setValue(value);
    }
}

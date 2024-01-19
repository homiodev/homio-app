package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.homio.api.ui.field.action.v1.item.UIIconPickerItemBuilder;

@Getter
@Accessors(chain = true)
public class UIIconPickerBuilderImpl
    extends UIBaseEntityItemBuilderImpl<UIIconPickerItemBuilder, String>
    implements UIIconPickerItemBuilder {

    public UIIconPickerBuilderImpl(String entityID, int order, String icon) {
        super(UIItemType.IconPicker, entityID, order, null);
        setValue(icon);
    }
}

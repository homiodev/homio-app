package org.touchhome.app.builder.ui;

import lombok.Getter;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder;

@Getter
public class UIDurationBuilderImpl extends UIBaseEntityItemBuilderImpl<UIInfoItemBuilder, Long> {

    public UIDurationBuilderImpl(String entityID, int order, long value, String color) {
        super(UIItemType.Duration, entityID, order, null);
        setValue(value);
        this.setColor(color);
    }
}

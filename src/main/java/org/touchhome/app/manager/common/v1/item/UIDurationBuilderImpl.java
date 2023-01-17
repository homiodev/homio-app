package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder;

@Getter
public class UIDurationBuilderImpl extends UIBaseEntityItemBuilderImpl<UIInfoItemBuilder, Long> {

    public UIDurationBuilderImpl(String entityID, int order, long value, String color) {
        super(UIItemType.Duration, entityID, order, null);
        setValue(value);
        this.setColor(color);
    }
}

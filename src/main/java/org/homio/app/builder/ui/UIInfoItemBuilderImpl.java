package org.homio.app.builder.ui;

import lombok.Getter;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder;

@Getter
public class UIInfoItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UIInfoItemBuilder, String>
        implements UIInfoItemBuilder {

    private final InfoType infoType;

    public UIInfoItemBuilderImpl(            String entityID, int order, String value, UIInfoItemBuilder.InfoType infoType) {
        super(UIItemType.Text, entityID, order, null);
        this.infoType = infoType;
        setValue(value);
    }
}

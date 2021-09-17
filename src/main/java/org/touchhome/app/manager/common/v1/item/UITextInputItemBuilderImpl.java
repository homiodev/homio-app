package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.field.action.v1.item.UITextInputItemBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
public class UITextInputItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UITextInputItemBuilder, String>
        implements UITextInputItemBuilder {

    private final InputType inputType;
    private boolean required;
    private final List<String> validators = new ArrayList<>();

    public UITextInputItemBuilderImpl(String entityID, int order, String defaultValue, InputType inputType) {
        super(UIItemType.Input, entityID, order, null);
        setValue(defaultValue);
        this.inputType = inputType;
    }

    public UITextInputItemBuilderImpl setRequired(boolean required) {
        this.required = required;
        return this;
    }
}

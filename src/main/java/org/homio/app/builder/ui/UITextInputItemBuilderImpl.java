package org.homio.app.builder.ui;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.homio.bundle.api.ui.field.action.v1.item.UITextInputItemBuilder;

@Getter
public class UITextInputItemBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UITextInputItemBuilder, String>
        implements UITextInputItemBuilder {

    private final InputType inputType;
    private final List<String> validators = new ArrayList<>();
    private boolean required;

    public UITextInputItemBuilderImpl(
            String entityID, int order, String defaultValue, InputType inputType) {
        super(UIItemType.Input, entityID, order, null);
        setValue(defaultValue);
        this.inputType = inputType;
    }

    public UITextInputItemBuilderImpl setRequired(boolean required) {
        this.required = required;
        return this;
    }
}

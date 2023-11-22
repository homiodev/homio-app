package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Accessors(chain = true)
public class UITextInputItemBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UITextInputItemBuilder, String>
        implements UITextInputItemBuilder {

    private final InputType inputType;
    private final List<String> validators = new ArrayList<>();
    @Setter
    private boolean required;
    @Setter
    private boolean applyButton;

    public UITextInputItemBuilderImpl(String entityID, int order, String defaultValue, InputType inputType) {
        super(UIItemType.Input, entityID, order, null);
        setValue(defaultValue);
        this.inputType = inputType;
    }
}

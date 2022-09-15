package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasTextConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup("Text")
    @UIFieldCodeEditor(autoFormat = true, editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    default String getTextConverter() {
        return getJsonData("textConv", "return value;");
    }

    default void setTextConverter(String value) {
        setJsonData("textConv", value);
    }
}

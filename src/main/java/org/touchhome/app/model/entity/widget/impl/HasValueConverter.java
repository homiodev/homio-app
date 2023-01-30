package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.bundle.api.ui.field.MonacoLanguage;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasValueConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup("Value")
    @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
    default String getValueConverter() {
        return getJsonData("valConv", "return value;");
    }

    default void setValueConverter(String value) {
        setJsonData("valConv", value);
    }
}

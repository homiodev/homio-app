package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.bundle.api.ui.field.MonacoLanguage;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasTextConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup("Text")
    @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
    @UIFieldReadDefaultValue
    default String getTextConverter() {
        return getJsonData("textConv", "return value;");
    }

    default void setTextConverter(String value) {
        setJsonData("textConv", value);
    }
}

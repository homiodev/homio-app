package org.homio.app.model.entity.widget.attributes;

import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasTextConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup("TEXT")
    @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
    @UIFieldReadDefaultValue
    default String getTextConverter() {
        return getJsonData("textConv", "return value;");
    }

    default void setTextConverter(String value) {
        setJsonData("textConv", value);
    }
}

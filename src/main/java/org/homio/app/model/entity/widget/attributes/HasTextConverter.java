package org.homio.app.model.entity.widget.attributes;

import org.homio.bundle.api.ui.field.MonacoLanguage;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldCodeEditor;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

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

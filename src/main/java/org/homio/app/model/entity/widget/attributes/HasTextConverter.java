package org.homio.app.model.entity.widget.attributes;

import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasTextConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup(value = "TEXT", order = 19, borderColor = "#FFBF61")
    @UIFieldTab(value = "ADVANCED", order = 10, color = "#FD8B51")
    @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
    @UIFieldReadDefaultValue
    default String getTextConverter() {
        return getJsonData("textConv", "return value;");
    }

    default void setTextConverter(String value) {
        setJsonData("textConv", value);
    }
}

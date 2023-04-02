package org.homio.app.model.entity.widget.attributes;

import org.homio.bundle.api.ui.field.MonacoLanguage;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldCodeEditor;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.UIFieldSlider;
import org.homio.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasValueConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup("Value")
    @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
    @UIFieldReadDefaultValue
    default String getValueConverter() {
        return getJsonData("valConv", "return value;");
    }

    default void setValueConverter(String value) {
        setJsonData("valConv", value);
    }

    @UIField(order = 250)
    @UIFieldSlider(min = 0, max = 60)
    @UIFieldGroup("Value")
    @UIFieldReadDefaultValue
    default int getValueConverterInterval() {
        return getJsonData("valConvInt", 0);
    }

    default void setValueConverterInterval(int value) {
        setJsonData("valConvInt", value);
    }
}

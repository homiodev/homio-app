package org.homio.app.model.entity.widget.attributes;

import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasValueConverter extends HasDynamicParameterFields {

    @UIField(order = 100)
    @UIFieldGroup(value = "VALUE", order = 1, borderColor = "#605678")
    @UIFieldTab(value = "ADVANCED", order = 10, color = "#FD8B51")
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
    @UIFieldGroup("VALUE")
    @UIFieldTab("ADVANCED")
    @UIFieldReadDefaultValue
    default int getValueConverterInterval() {
        return getJsonData("valConvInt", 0);
    }

    default void setValueConverterInterval(int value) {
        setJsonData("valConvInt", value);
    }
}

package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public interface HasValueTemplate extends HasJsonData {

    @UIField(order = 200, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 10)
    @UIFieldUpdateFontSize
    default String getValueTemplate() {
        return getJsonData("vt", "~~~℃");
    }

    @UIField(order = 240)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    default String getValueColor() {
        return getJsonData("vc", UI.Color.WHITE);
    }

    @UIField(order = 280)
    @UIFieldGroup("Value")
    default String getNoValueText() {
        return getJsonData("noVal", "-");
    }

    @UIField(order = 0, visible = false)
    default double getValueTemplateFontSize() {
        return getJsonData("vtfs", 1D);
    }

    default void setValueTemplateFontSize(double value) {
        setJsonData("vtfs", value);
    }

    @UIField(order = 0, visible = false)
    default double getValueTemplatePrefixFontSize() {
        return getJsonData("vtpfs", 1D);
    }

    default void setValueTemplatePrefixFontSize(double value) {
        setJsonData("vtpfs", value);
    }

    @UIField(order = 0, visible = false)
    default double getValueTemplateSuffixFontSize() {
        return getJsonData("vtsfs", 1D);
    }

    default void setValueTemplateSuffixFontSize(double value) {
        setJsonData("vtsfs", value);
    }

    default void setNoValueText(String value) {
        setJsonData("noVal", value);
    }

    default void setValueTemplate(String value) {
        setJsonData("vt", value);
    }

    default void setValueColor(String value) {
        setJsonData("valC", value);
    }
}

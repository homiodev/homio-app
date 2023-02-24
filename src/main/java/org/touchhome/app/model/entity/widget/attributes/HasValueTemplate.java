package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.app.model.entity.widget.UIFieldOptionColor;
import org.touchhome.app.model.entity.widget.UIFieldOptionFontSize;
import org.touchhome.app.model.entity.widget.UIFieldOptionVerticalAlign;
import org.touchhome.bundle.api.EntityContextWidget.VerticalAlign;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public interface HasValueTemplate extends HasJsonData {

    @UIField(order = 200, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 10)
    @UIFieldOptionFontSize("value")
    @UIFieldOptionVerticalAlign("value")
    @UIFieldOptionColor("value")
    default String getValueTemplate() {
        return getJsonData("vt", "~~~");
    }

    default void setValueTemplate(String value) {
        setJsonData("vt", value);
    }

    @UIField(order = 240, isRevert = true)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    @UIFieldReadDefaultValue
    default String getValueColor() {
        return getJsonData("valC", UI.Color.WHITE);
    }

    default void setValueColor(String value) {
        setJsonData("valC", value);
    }

    @UIField(order = 280)
    @UIFieldGroup("Value")
    @UIFieldReadDefaultValue
    default String getNoValueText() {
        return getJsonData("noVal", "-");
    }

    default void setNoValueText(String value) {
        setJsonData("noVal", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default double getValueFontSize() {
        return getJsonData("vtfs", 1D);
    }

    default void setValueFontSize(double value) {
        setJsonData("vtfs", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default double getValuePrefixFontSize() {
        return getJsonData("vtpfs", 1D);
    }

    default void setValuePrefixFontSize(double value) {
        setJsonData("vtpfs", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default double getValueSuffixFontSize() {
        return getJsonData("vtsfs", 1D);
    }

    default void setValueSuffixFontSize(double value) {
        setJsonData("vtsfs", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default VerticalAlign getValuePrefixVerticalAlign() {
        return getJsonDataEnum("vtpva", VerticalAlign.middle);
    }

    default void setValuePrefixVerticalAlign(VerticalAlign value) {
        setJsonData("vtpva", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default VerticalAlign getValueSuffixVerticalAlign() {
        return getJsonDataEnum("vtsva", VerticalAlign.middle);
    }

    default void setValueSuffixVerticalAlign(VerticalAlign value) {
        setJsonData("vtsva", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default VerticalAlign getValueVerticalAlign() {
        return getJsonDataEnum("vtva", VerticalAlign.middle);
    }

    default void setValueVerticalAlign(VerticalAlign value) {
        setJsonData("vtva", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    default String getValuePrefixColor() {
        return getJsonData("vtpc");
    }

    default void setValuePrefixColor(String value) {
        setJsonData("vtpc", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    default String getValueSuffixColor() {
        return getJsonData("vtsc");
    }

    default void setValueSuffixColor(String value) {
        setJsonData("vtsc", value);
    }
}

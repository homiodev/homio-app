package org.homio.app.model.entity.widget.attributes;

import org.homio.api.ContextWidget.VerticalAlign;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.model.entity.widget.UIFieldOptionColor;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.UIFieldOptionVerticalAlign;

public interface HasValueTemplate extends HasJsonData {

    @UIField(order = 200, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "VALUE", order = 10)
    @UIFieldOptionFontSize("value")
    @UIFieldOptionVerticalAlign("value")
    @UIFieldOptionColor("value")
    default String getValueTemplate() {
        return getJsonData("vt", LIST_DELIMITER);
    }

    default void setValueTemplate(String value) {
        setJsonData("vt", value);
    }

    @UIField(order = 240)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("VALUE")
    @UIFieldReadDefaultValue
    default String getValueColor() {
        return getJsonData("valC", UI.Color.WHITE);
    }

    default void setValueColor(String value) {
        setJsonData("valC", value);
    }

    @UIField(order = 280)
    @UIFieldGroup("VALUE")
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

    @UIField(order = 500)
    @UIFieldGroup("VALUE")
    default boolean getValueSourceClickHistory() {
        return getJsonData("vsch", false);
    }

    default void setValueSourceClickHistory(boolean value) {
        setJsonData("vsch", value);
    }
}

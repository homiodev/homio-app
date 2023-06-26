package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;

public interface HasHorizontalLine extends HasJsonData {

    @UIField(order = 1)
    @UIFieldGroup(value = "CHART_HL", order = 59, borderColor = "#953CEE")
    @UIFieldSlider(min = -1, max = 100)
    default Integer getSingleLinePos() {
        return getJsonData("slpos", -1);
    }

    default void setSingleLinePos(Integer value) {
        setJsonData("slpos", value);
    }

    @UIField(order = 2, isRevert = true)
    @UIFieldGroup("CHART_HL")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getSingleLineColor() {
        return getJsonData("slcol", UI.Color.RED);
    }

    default void setSingleLineColor(String value) {
        setJsonData("slcol", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("CHART_HL")
    @UIFieldSlider(min = 1, max = 10)
    default Integer getSingleLineWidth() {
        return getJsonData("slwidth", 1);
    }

    default void setSingleLineWidth(Integer value) {
        setJsonData("slwidth", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("CHART_HL")
    default Boolean isShowDynamicLine() {
        return getJsonData("sdyn", Boolean.FALSE);
    }

    default void setShowDynamicLine(Boolean value) {
        setJsonData("sdyn", value);
    }

    @UIField(order = 5, isRevert = true)
    @UIFieldGroup("CHART_HL")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getDynamicLineColor() {
        return getJsonData("dynlc", UI.Color.GREEN);
    }

    default void setDynamicLineColor(String value) {
        setJsonData("dynlc", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("CHART_HL")
    @UIFieldSlider(min = 1, max = 10)
    default Integer getDynamicLineWidth() {
        return getJsonData("dynlw", 1);
    }

    default void setDynamicLineWidth(Integer value) {
        setJsonData("dynlw", value);
    }
}

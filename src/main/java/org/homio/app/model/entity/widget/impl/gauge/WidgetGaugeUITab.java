package org.homio.app.model.entity.widget.impl.gauge;

import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldMarkers;

public interface WidgetGaugeUITab extends HasJsonData {

    @UIField(order = 1)
    @UIFieldTab(value = "UI", order = 2, color = "#257180")
    default WidgetGaugeEntity.GaugeType getDisplayType() {
        return getJsonDataEnum("displayType", WidgetGaugeEntity.GaugeType.arch);
    }

    default void setDisplayType(WidgetGaugeEntity.GaugeType value) {
        setJsonDataEnum("displayType", value);
    }

    @UIField(order = 4, type = UIFieldType.Slider, label = "gauge.thick")
    @UIFieldNumber(min = 1, max = 20)
    @UIFieldTab("UI")
    default Integer getThick() {
        return getJsonData("thick", 6);
    }

    default void setThick(Integer value) {
        setJsonData("thick", value);
    }

    @UIField(order = 5)
    @UIFieldTab("UI")
    default WidgetGaugeEntity.LineType getGaugeCapType() {
        return getJsonDataEnum("gaugeCapType", WidgetGaugeEntity.LineType.round);
    }

    default void setGaugeCapType(WidgetGaugeEntity.LineType lineType) {
        setJsonDataEnum("gaugeCapType", lineType);
    }

    @UIField(order = 6)
    @UIFieldTab("UI")
    @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true)
    @UIFieldReadDefaultValue
    default String getGaugeForeground() {
        return getJsonData("gfc", UI.Color.PRIMARY_COLOR);
    }

    default void setGaugeForeground(String value) {
        setJsonData("gfc", value);
    }

    @UIField(order = 7)
    @UIFieldTab("UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getGaugeBackground() {
        return getJsonData("gbg", "#444444");
    }

    default void setGaugeBackground(String value) {
        setJsonData("gbg", value);
    }

    @UIField(order = 8)
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldTab("UI")
    @UIFieldReadDefaultValue
    default int getDotBorderWidth() {
        return getJsonData("dotbw", 0);
    }

    default void setDotBorderWidth(int value) {
        setJsonData("dotbw", value);
    }

    @UIField(order = 9)
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldTab("UI")
    @UIFieldReadDefaultValue
    default int getDotRadiusWidth() {
        return getJsonData("dotbrw", 0);
    }

    default void setDotRadiusWidth(int value) {
        setJsonData("dotbrw", value);
    }

    @UIField(order = 10)
    @UIFieldTab("UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getDotBorderColor() {
        return getJsonData("dotbc", UI.Color.WHITE);
    }

    default void setDotBorderColor(String value) {
        setJsonData("dotbc", value);
    }

    @UIField(order = 11)
    @UIFieldTab("UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getDotColor() {
        return getJsonData("dotc", UI.Color.WHITE);
    }

    default void setDotColor(String value) {
        setJsonData("dotc", value);
    }

    @UIField(order = 12)
    @UIFieldTab("UI")
    default Boolean getDrawForegroundAsSegments() {
        return getJsonData("dfass", Boolean.FALSE);
    }

    default void setDrawForegroundAsSegments(Boolean value) {
        setJsonData("dfass", value);
    }

    @UIField(order = 13)
    @UIFieldTab("UI")
    default Boolean getDrawBackgroundAsSegments() {
        return getJsonData("dbass", Boolean.FALSE);
    }

    default void setDrawBackgroundAsSegments(Boolean value) {
        setJsonData("dbass", value);
    }

    @UIField(order = 14, type = UIFieldType.Slider)
    @UIFieldSlider(min = 1, max = 200, step = 2)
    @UIFieldTab("UI")
    default Integer getSegmentLength() {
        return getJsonData("seg_len", 1);
    }

    default void setSegmentLength(Integer value) {
        setJsonData("seg_len", value);
    }

    @UIField(order = 14, type = UIFieldType.Slider)
    @UIFieldSlider(min = 1, max = 200, step = 2)
    @UIFieldTab("UI")
    default Integer getSegmentGap() {
        return getJsonData("seg_gap", 1);
    }

    default void setSegmentGap(Integer value) {
        setJsonData("seg_gap", value);
    }

    @UIField(order = 20)
    @UIFieldTab("UI")
    @UIFieldMarkers(UIFieldMarkers.MarkerOP.opacity)
    default String getSliceThreshold() {
        return getJsonData("slices", "");
    }

    default void setSliceThreshold(String value) {
        setJsonData("slices", value);
    }

    @UIField(order = 25)
    @UIFieldTab("UI")
    @UIFieldSlider(min = 0, max = 1500, step = 100)
    default int getAnimateDuration() {
        return getJsonData("animdur", 500);
    }

    default void getAnimateDuration(int value) {
        setJsonData("animdur", value);
    }

    @UIField(order = 30)
    @UIFieldTab("UI")
    default String getBackground() {
        return getJsonData("back");
    }

    default void setBackground(String value) {
        setJsonData("back", value);
    }

    @UIField(order = 35)
    @UIFieldTab("UI")
    default Boolean getDrawNeedle() {
        return getJsonData("ndl", Boolean.FALSE);
    }

    default void setDrawNeedle(Boolean value) {
        setJsonData("ndl", value);
    }

    @UIField(order = 40)
    @UIFieldTab("UI")
    @UIFieldColorPicker
    default String getNeedleColor() {
        return getJsonData("ndlClr", UI.Color.PRIMARY_COLOR);
    }

    default void setNeedleColor(String value) {
        setJsonData("ndlClr", value);
    }

    @UIField(order = 1)
    @UIFieldTab("UI")
    @UIFieldGroup(value = "MARKERS", order = 500, borderColor = "#1F85B8")
    @UIFieldMarkers(UIFieldMarkers.MarkerOP.label)
    default String getMarkers() {
        return getJsonData("markers", "");
    }

    default void setMarkers(String value) {
        setJsonData("markers", value);
    }

    @UIField(order = 2)
    @UIFieldTab("UI")
    @UIFieldGroup("MARKERS")
    default WidgetGaugeEntity.MarkerType getMarkerType() {
        return getJsonDataEnum("mt", WidgetGaugeEntity.MarkerType.line);
    }

    default void setMarkerType(WidgetGaugeEntity.MarkerType value) {
        setJsonDataEnum("mt", value);
    }

    @UIField(order = 3)
    @UIFieldTab("UI")
    @UIFieldGroup("MARKERS")
    @UIFieldSlider(min = 8, max = 14)
    default int getMarkerFontSize() {
        return getJsonData("mfs", 12);
    }

    default void setMarkerFontSize(int value) {
        setJsonData("mfs", value);
    }

    @UIField(order = 4)
    @UIFieldTab("UI")
    @UIFieldGroup("MARKERS")
    @UIFieldSlider(min = 2, max = 16)
    default int getMarkerSize() {
        return getJsonData("ms", 8);
    }

    default void setMarkerSize(int value) {
        setJsonData("ms", value);
    }

    @UIField(order = 5)
    @UIFieldTab("UI")
    @UIFieldGroup("MARKERS")
    default Boolean getMarkerInvert() {
        return getJsonData("minv", Boolean.FALSE);
    }

    default void setMarkerInvert(Boolean value) {
        setJsonData("minv", value);
    }
}

package org.homio.app.model.entity.widget.impl.gauge;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
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
    default String getForeground() {
        return getJsonData("gfc", UI.Color.PRIMARY_COLOR);
    }

    default void setForeground(String value) {
        setJsonData("gfc", value);
    }

    @UIField(order = 7)
    @UIFieldTab("UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default String getBackground() {
        return getJsonData("gbg", "#444444");
    }

    default void setBackground(String value) {
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

    @UIField(order = 20)
    @UIFieldTab("UI")
    @UIFieldMarkers(UIFieldMarkers.MarkerOP.opacity)
    default String getSliceThreshold() {
        return getJsonData("slices", "");
    }

    default void setSliceThreshold(String value) {
        setJsonData("slices", value);
    }

    @UIField(order = 24)
    @UIFieldTab("UI")
    default Boolean getAnimations() {
        return getJsonData("animations", Boolean.FALSE);
    }

    default void setAnimations(Boolean value) {
        setJsonData("animations", value);
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

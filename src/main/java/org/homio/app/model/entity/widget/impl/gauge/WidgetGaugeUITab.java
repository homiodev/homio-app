package org.homio.app.model.entity.widget.impl.gauge;

import org.homio.api.ContextWidget;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldNumber;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.model.entity.widget.UIFieldMarkers;

public interface WidgetGaugeUITab extends HasJsonData {

  @UIField(order = 1)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  @UIFieldSlider(min = 0, max = 1500, step = 100)
  default int getAnimateDuration() {
    return getJsonData("animdur", 500);
  }

  default void setAnimateDuration(int value) {
    setJsonData("animdur", value);
  }

  @UIField(order = 2)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  default ContextWidget.GaugeDisplayType getDisplayType() {
    return getJsonDataEnum("displayType", ContextWidget.GaugeDisplayType.arch);
  }

  default void setDisplayType(ContextWidget.GaugeDisplayType value) {
    setJsonDataEnum("displayType", value);
  }

  @UIField(order = 4, type = UIFieldType.Slider, label = "gauge.thick")
  @UIFieldNumber(min = 1, max = 20)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  default Integer getThick() {
    return getJsonData("thick", 6);
  }

  default void setThick(Integer value) {
    setJsonData("thick", value);
  }

  @UIField(order = 5)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  default ContextWidget.GaugeCapType getGaugeCapType() {
    return getJsonDataEnum("gaugeCapType", ContextWidget.GaugeCapType.round);
  }

  default void setGaugeCapType(ContextWidget.GaugeCapType lineType) {
    setJsonDataEnum("gaugeCapType", lineType);
  }

  @UIField(order = 6)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true)
  default String getGaugeForeground() {
    return getJsonData("gfc", UI.Color.PRIMARY_COLOR);
  }

  default void setGaugeForeground(String value) {
    setJsonData("gfc", value);
  }

  @UIField(order = 7)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  @UIFieldColorPicker
  default String getGaugeBackground() {
    return getJsonData("gbg", "#444444");
  }

  default void setGaugeBackground(String value) {
    setJsonData("gbg", value);
  }

  @UIField(order = 8)
  @UIFieldTab("UI")
  @UIFieldGroup("GENERAL")
  default Boolean isShowGradient() {
    return getJsonData("sg", Boolean.FALSE);
  }

  default void setShowGradient(boolean value) {
    setJsonData("sg", value);
  }

  @UIField(order = 1)
  @UIFieldSlider(min = 0, max = 20)
  @UIFieldTab("UI")
  @UIFieldGroup(value = "DOT", order = 300, borderColor = "#C2365B")
  default int getDotBorderWidth() {
    return getJsonData("dotbw", 2);
  }

  default void setDotBorderWidth(int value) {
    setJsonData("dotbw", value);
  }

  @UIField(order = 2)
  @UIFieldSlider(min = 0, max = 20)
  @UIFieldTab("UI")
  @UIFieldGroup("DOT")
  default int getDotRadiusWidth() {
    return getJsonData("dotbrw", 5);
  }

  default void setDotRadiusWidth(int value) {
    setJsonData("dotbrw", value);
  }

  @UIField(order = 3)
  @UIFieldTab("UI")
  @UIFieldGroup("DOT")
  @UIFieldColorPicker
  default String getDotBorderColor() {
    return getJsonData("dotbc", UI.Color.WHITE);
  }

  default void setDotBorderColor(String value) {
    setJsonData("dotbc", value);
  }

  @UIField(order = 4)
  @UIFieldTab("UI")
  @UIFieldGroup("DOT")
  @UIFieldColorPicker
  default String getDotColor() {
    return getJsonData("dotc", UI.Color.WHITE);
  }

  default void setDotColor(String value) {
    setJsonData("dotc", value);
  }

  @UIField(order = 12)
  @UIFieldGroup(value = "SEGMENTS", order = 400, borderColor = "#8926C7")
  @UIFieldTab("UI")
  default boolean getDrawForegroundAsSegments() {
    return getJsonData("dfass", Boolean.FALSE);
  }

  default void setDrawForegroundAsSegments(boolean value) {
    setJsonData("dfass", value);
  }

  @UIField(order = 13)
  @UIFieldTab("UI")
  @UIFieldGroup("SEGMENTS")
  default boolean getDrawBackgroundAsSegments() {
    return getJsonData("dbass", Boolean.FALSE);
  }

  default void setDrawBackgroundAsSegments(boolean value) {
    setJsonData("dbass", value);
  }

  @UIField(order = 14, type = UIFieldType.Slider)
  @UIFieldSlider(min = 1, max = 200, step = 2)
  @UIFieldTab("UI")
  @UIFieldGroup("SEGMENTS")
  default Integer getSegmentLength() {
    return getJsonData("seg_len", 1);
  }

  default void setSegmentLength(Integer value) {
    setJsonData("seg_len", value);
  }

  @UIField(order = 14, type = UIFieldType.Slider)
  @UIFieldSlider(min = 1, max = 200, step = 2)
  @UIFieldTab("UI")
  @UIFieldGroup("SEGMENTS")
  default Integer getSegmentGap() {
    return getJsonData("seg_gap", 1);
  }

  default void setSegmentGap(Integer value) {
    setJsonData("seg_gap", value);
  }

  @UIField(order = 20)
  @UIFieldTab("UI")
  @UIFieldMarkers(UIFieldMarkers.MarkerOP.none)
  default String getSliceThreshold() {
    return getJsonData("slices", "");
  }

  default void setSliceThreshold(String value) {
    setJsonData("slices", value);
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
  @UIFieldGroup(value = "NEEDLE", order = 450, borderColor = "#ACB82A")
  default boolean getDrawNeedle() {
    return getJsonData("ndl", Boolean.FALSE);
  }

  default void setDrawNeedle(boolean value) {
    setJsonData("ndl", value);
  }

  @UIField(order = 40)
  @UIFieldTab("UI")
  @UIFieldGroup("NEEDLE")
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
  default boolean getMarkerInvert() {
    return getJsonData("minv", Boolean.FALSE);
  }

  default void setMarkerInvert(boolean value) {
    setJsonData("minv", value);
  }
}

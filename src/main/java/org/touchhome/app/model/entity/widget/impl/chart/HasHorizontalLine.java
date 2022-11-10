package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

public interface HasHorizontalLine extends HasJsonData {

  @UIField(order = 1)
  @UIFieldGroup(value = "CHART_HL", order = 120, borderColor = "#953CEE")
  @UIFieldSlider(min = -1, max = 100)
  default Integer getSingleLinePos() {
    return getJsonData("slpos", -1);
  }

  default void setSingleLinePos(Integer value) {
    setJsonData("slpos", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("CHART_HL")
  @UIFieldColorPicker
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

  @UIField(order = 5)
  @UIFieldGroup("CHART_HL")
  @UIFieldColorPicker
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
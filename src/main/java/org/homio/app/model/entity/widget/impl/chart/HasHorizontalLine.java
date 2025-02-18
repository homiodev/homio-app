package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;

public interface HasHorizontalLine extends HasJsonData {

  @UIField(order = 1)
  @UIFieldGroup(order = 59, value = "CHART_HL", borderColor = "#953CEE")
  @UIFieldSlider(min = 0, max = 100)
  default Integer getSingleLinePos() {
    return getJsonData("slpos", 50);
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
  @UIFieldSlider(min = 0, max = 10)
  default Integer getSingleLineWidth() {
    return getJsonData("slwidth", 0);
  }

  default void setSingleLineWidth(Integer value) {
    setJsonData("slwidth", value);
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
  @UIFieldSlider(min = 0, max = 10)
  default Integer getDynamicLineWidth() {
    return getJsonData("dynlw", 0);
  }

  default void setDynamicLineWidth(Integer value) {
    setJsonData("dynlw", value);
  }
}

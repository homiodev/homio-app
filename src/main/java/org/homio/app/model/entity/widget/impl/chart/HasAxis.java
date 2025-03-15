package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.ContextWidget;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;

public interface HasAxis extends HasJsonData {

  @UIField(order = 80)
  @UIFieldGroup("CHART_AXIS")
  default Boolean getShowAxisX() {
    return getJsonData("showAxisX", Boolean.FALSE);
  }

  default void setShowAxisX(Boolean value) {
    setJsonData("showAxisX", value);
  }

  @UIField(order = 81)
  @UIFieldGroup("CHART_AXIS")
  default Boolean getShowAxisY() {
    return getJsonData("showAxisY", Boolean.FALSE);
  }

  default void setShowAxisY(Boolean value) {
    setJsonData("showAxisY", value);
  }

  @UIField(order = 84)
  @UIFieldGroup("CHART_AXIS")
  default String getAxisLabelX() {
    return getJsonData("axisLabelX");
  }

  default void setAxisLabelX(String value) {
    setJsonData("axisLabelX", value);
  }

  @UIField(order = 85)
  @UIFieldGroup("CHART_AXIS")
  default String getAxisLabelY() {
    return getJsonData("axisLabelY");
  }

  default void setAxisLabelY(String value) {
    setJsonData("axisLabelY", value);
  }

  @UIField(order = 90)
  @UIFieldGroup("CHART_AXIS")
  default ContextWidget.AxisDateFormat getAxisDateFormat() {
    return getJsonDataEnum("timeF", ContextWidget.AxisDateFormat.auto);
  }

  default void setAxisDateFormat(ContextWidget.AxisDateFormat value) {
    setJsonData("timeF", value);
  }
}

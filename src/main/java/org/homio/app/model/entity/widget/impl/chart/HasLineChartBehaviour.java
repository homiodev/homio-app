package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.ContextWidget.ChartType;
import org.homio.api.ContextWidget.Fill;
import org.homio.api.ContextWidget.PointStyle;
import org.homio.api.ContextWidget.Stepped;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;

public interface HasLineChartBehaviour extends
  HasJsonData,
  HasMinMaxChartValue,
  HasChartTimePeriod {

  ChartType getChartType();

  @UIField(order = 20)
  @UIFieldGroup(order = 54, value = "CHART_UI", borderColor = "#673AB7")
  @UIFieldSlider(min = 0, max = 10)
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default int getLineBorderWidth() {
    return getJsonData("lbw", 2);
  }

  default void setLineBorderWidth(int value) {
    setJsonData("lbw", value);
  }

  @UIField(order = 40)
  @UIFieldGroup("CHART_UI")
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default Fill getLineFill() {
    return getJsonDataEnum("fill", Fill.Origin);
  }

  default void setLineFill(Fill value) {
    setJsonDataEnum("fill", value);
  }

  @UIField(order = 6)
  @UIFieldGroup("CHART_UI")
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default Stepped getStepped() {
    return getJsonDataEnum("stpd", Stepped.False);
  }

  default void setStepped(Stepped value) {
    setJsonDataEnum("stpd", value);
  }

  @UIField(order = 3)
  @UIFieldSlider(min = 0, max = 10)
  @UIFieldGroup("CHART_UI")
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default int getTension() {
    return getJsonData("tns", 4);
  }

  default void setTension(int value) {
    setJsonData("tns", value);
  }

    /*@UIField(order = 5)
    @UIFieldSlider(min = 10, max = 600)
    @UIFieldGroup("CHART_UI")
    default int getFetchDataFromServerInterval() {
        return getJsonData("fsfsi", 60);
    }

    default void setFetchDataFromServerInterval(int value) {
        setJsonData("fsfsi", value);
    }*/

  @UIField(order = 1)
  @UIFieldGroup(order = 58, value = "CHART_POINT")
  @UIFieldSlider(min = 0, max = 4, step = 0.2)
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default double getPointRadius() {
    return getJsonData("prad", 0.0);
  }

  default void setPointRadius(double value) {
    setJsonData("prad", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("CHART_POINT")
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default PointStyle getPointStyle() {
    return getJsonDataEnum("pstyle", PointStyle.circle);
  }

  default void setPointStyle(PointStyle value) {
    setJsonDataEnum("pstyle", value);
  }

  @UIField(order = 3)
  @UIFieldGroup("CHART_POINT")
  @UIFieldColorPicker
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default String getPointBackgroundColor() {
    return getJsonData("pbg", UI.Color.WHITE);
  }

  default void setPointBackgroundColor(String value) {
    setJsonData("pbg", value);
  }

  @UIField(order = 4)
  @UIFieldGroup("CHART_POINT")
  @UIFieldColorPicker
  @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
  default String getPointBorderColor() {
    return getJsonData("pbc", UI.Color.PRIMARY_COLOR);
  }

  default void setPointBorderColor(String value) {
    setJsonData("pbc", value);
  }
}

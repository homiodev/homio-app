package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.rest.widget.ChartDataset;
import org.homio.app.rest.widget.EvaluateDatesAndValues;
import org.homio.app.rest.widget.TimeSeriesContext;

public interface HasChartDataSource extends HasJsonData {

  static void randomColor(HasJsonData widget) {
    String randomColor = UI.Color.random();
    if (!widget.getJsonData().has("chartC")) {
      widget.setJsonData("chartC", randomColor);
    }
  }

  @UIField(order = 1)
  @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
  @UIFieldGroup(order = 50, value = "CHART", borderColor = "#9C27B0")
  @UIEditReloadWidget
  default String getChartDataSource() {
    return getJsonData("chartDS");
  }

  default void setChartDataSource(String value) {
    setJsonData("chartDS", value);
  }

  @UIField(order = 5)
  @UIFieldGroup("CHART")
  @UIEditReloadWidget
  default AggregationType getChartAggregationType() {
    return getJsonDataEnum("chartAggrType", AggregationType.AverageNoZero);
  }

  default void setChartAggregationType(AggregationType value) {
    setJsonData("chartAggrType", value);
  }

  @UIField(order = 6)
  @UIFieldGroup("CHART")
  @UIFieldCodeEditor(autoFormat = true, editorType = MonacoLanguage.JavaScript)
  default String getFinalChartValueConverter() {
    return getJsonData("finValConv", "return value;");
  }

  default void setFinalChartValueConverter(String value) {
    setJsonData("finValConv", value);
  }

  @UIField(order = 1)
  @UIFieldGroup(order = 54, value = "CHART_UI", borderColor = "#673AB7")
  @UIFieldColorPicker(allowThreshold = true)
  default String getChartColor() {
    return getJsonData("chartC", UI.Color.WHITE);
  }

  default void setChartColor(String value) {
    setJsonData("chartC", value);
  }

  @UIField(order = 2)
  @UIFieldSlider(min = 0, max = 100, step = 5)
  @UIFieldGroup("CHART_UI")
  default int getChartColorOpacity() {
    return getJsonData("chartCO", 50);
  }

  default void setChartColorOpacity(int value) {
    setJsonData("chartCO", value);
  }

  @UIField(order = 4)
  @UIFieldGroup("CHART_UI")
  default Boolean getFillEmptyValues() {
    return getJsonData("fev", Boolean.FALSE);
  }

  default void setFillEmptyValues(Boolean value) {
    setJsonData("fev", value);
  }

  @UIField(order = 5)
  @UIFieldGroup("CHART_UI")
  default String getChartLabel() {
    return getJsonData("clbl", "");
  }

  default void setChartLabel(String value) {
    setJsonData("clbl", value);
  }

  @UIField(order = 6)
  @UIFieldGroup("CHART_UI")
  default boolean getSmoothing() {
    return getJsonData("sm", true);
  }

  default void setSmoothing(boolean value) {
    setJsonData("sm", value);
  }

  default ChartDataset buildTargetDataset(TimeSeriesContext item) {
    HasChartDataSource seriesEntity = item.getSeriesEntity();
    String entityID = ((HasEntityIdentifier) seriesEntity).getEntityID();
    ChartDataset dataset = new ChartDataset(item.getId(), entityID).setLabel(seriesEntity.getChartLabel());
    if (item.getValues() != null && !item.getValues().isEmpty()) {
      dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
    }
    return dataset;
  }
}

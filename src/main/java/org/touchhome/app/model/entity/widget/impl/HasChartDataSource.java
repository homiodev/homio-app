package org.touchhome.app.model.entity.widget.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasChartDataSource<T> extends HasJsonData<T> {

    @UIField(order = 1)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldGroup(value = "Chart", order = 10, borderColor = "#9C27B0")
    default String getChartDataSource() {
        return getJsonData("chartDS");
    }

    default void setChartDataSource(String value) {
        setJsonData("chartDS", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 72)
    @UIFieldGroup("Chart")
    default int getHoursToShow() {
        return getJsonData("hts", 24);
    }

    default void setHoursToShow(int value) {
        setJsonData("hts", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 1, max = 60)
    @UIFieldGroup("Chart")
    default int getPointsPerHour() {
        return getJsonData("pph", 1);
    }

    default void setPointsPerHour(int value) {
        setJsonData("pph", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("Chart")
    default AggregationType getChartAggregationType() {
        return getJsonDataEnum("chartAggrType", AggregationType.Average);
    }

    default void setChartAggregationType(AggregationType value) {
        setJsonData("chartAggrType", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("Chart")
    default Boolean getFillMissingValues() {
        return getJsonData("fillMis", false);
    }

    default void setFillMissingValues(Boolean value) {
        setJsonData("fillMis", value);
    }

    @UIField(order = 7)
    @UIFieldGroup("Chart")
    default ChartType getChartType() {
        return getJsonDataEnum("ct", ChartType.line);
    }

    default void getChartType(ChartType value) {
        setJsonData("ct", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Chart ui", order = 4, borderColor = "#673AB7")
    @UIFieldColorPicker(allowThreshold = true)
    default String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    default void setChartColor(String value) {
        setJsonData("chartC", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 0, max = 100, step = 5)
    @UIFieldGroup("Chart ui")
    default int getChartColorOpacity() {
        return getJsonData("chartCO", 50);
    }

    default void setChartColorOpacity(int value) {
        setJsonData("chartCO", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("Chart ui")
    default int getTension() {
        return getJsonData("tns", 4);
    }

    default void setTension(int value) {
        setJsonData("tns", value);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 20, max = 100)
    @UIFieldGroup("Chart ui")
    default int getChartHeight() {
        return getJsonData("ch", 30);
    }

    default void setChartHeight(int value) {
        setJsonData("ch", value);
    }

    @UIField(order = 5)
    @UIFieldGroup(value = "Chart ui")
    default String getChartLabel() {
        return getJsonData("clbl", "");
    }

    default void setChartLabel(String value) {
        setJsonData("clbl", value);
    }

    @UIField(order = 6)
    @UIFieldGroup(value = "Chart ui")
    default Stepped getStepped() {
        return getJsonDataEnum("stpd", Stepped.False);
    }

    default void setStepped(Stepped value) {
        setJsonDataEnum("stpd", value);
    }

    default void setInitChartColor(String color) {
        if (!getJsonData().has("chartC")) {
            setChartColor(color);
        }
    }

    default ChartDataset buildTargetDataset(TimeSeriesContext item) {
        HasChartDataSource seriesEntity = (HasChartDataSource) item.getSeriesEntity();
        WidgetChartsController.TimeSeriesDataset dataset = new WidgetChartsController.TimeSeriesDataset(item.getId(),
                seriesEntity.getChartLabel(), seriesEntity.getChartColor(), seriesEntity.getChartColorOpacity(),
                seriesEntity.getTension() / 10D, seriesEntity.getStepped().getValue());
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    enum ChartType {
        line, bar
    }

    @RequiredArgsConstructor
    enum Stepped {
        False(false),
        True(true),
        Before("before"),
        After("after"),
        Middle("middle");

        @Getter
        private final Object value;
    }
}

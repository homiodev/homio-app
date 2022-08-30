package org.touchhome.app.model.entity.widget.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
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

    @UIField(order = 1)
    @UIFieldGroup(value = "Chart ui", order = 11, borderColor = "#673AB7")
    @UIFieldColorPicker(allowThreshold = true)
    default String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    default void setChartColor(String value) {
        setJsonData("chartC", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 25, max = 100, step = 5)
    @UIFieldGroup("Chart ui")
    default int getChartColorOpacity() {
        return getJsonData("chartCO", 50);
    }

    default void setChartColorOpacity(int value) {
        setJsonData("chartCO", value);
    }

    @UIField(order = 5)
    @UIFieldGroup(value = "Chart ui")
    default String getChartLabel() {
        return getJsonData("clbl", "");
    }

    default void setChartLabel(String value) {
        setJsonData("clbl", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Chart axis", order = 100, borderColor = "#0C73A6")
    default Integer getMin() {
        return getJsonData().has("min") ? getJsonData().getInt("min") : null;
    }

    default void setMin(Integer value) {
        setJsonData("min", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("Chart axis")
    default Integer getMax() {
        return getJsonData().has("max") ? getJsonData().getInt("max") : null;
    }

    default void setMax(Integer value) {
        setJsonData("max", value);
    }

    default void setInitChartColor(String color) {
        if (!getJsonData().has("chartC")) {
            setChartColor(color);
        }
    }

    default ChartDataset buildTargetDataset(TimeSeriesContext item) {
        HasChartDataSource seriesEntity = item.getSeriesEntity();
        ChartDataset dataset = new ChartDataset(item.getId()).setLabel(seriesEntity.getChartLabel());
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    enum ChartType {
        line, bar
    }
}

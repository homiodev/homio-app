package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetMiniCardChartEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasTimeValueAndLastValueSeries;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasChartDataSource<T> extends HasJsonData<T> {

    @UIField(order = 1)
    @UIFieldEntityByClassSelection(HasTimeValueAndLastValueSeries.class)
    @UIFieldGroup(value = "Chart", order = 4)
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

    default void setHoursToShow() {
        setJsonData("hts", 24);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0.1, max = 60, step = 0.2)
    @UIFieldGroup("Chart")
    default double getPointsPerHour() {
        return getJsonData("pph", 0.5D);
    }

    @UIField(order = 5)
    @UIFieldGroup("Chart")
    default AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Average);
    }

    @UIField(order = 6)
    @UIFieldGroup("Chart")
    default Boolean getFillMissingValues() {
        return getJsonData("fillMis", false);
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
    @UIFieldGroup(value = "Chart ui", order = 4)
    @UIFieldColorPicker(allowThreshold = true)
    default String getChartColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    default void setChartColor(String value) {
        setJsonData("bc", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Chart ui")
    default int getChartColorOpacity() {
        return getJsonData("bco", 120);
    }

    default void setChartColorOpacity(int value) {
        setJsonData("bco", value);
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

    enum ChartType {
        line, bar
    }
}

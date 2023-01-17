package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasChartDataSource extends HasJsonData {

    static void randomColor(HasJsonData widget) {
        String randomColor = UI.Color.random();
        if (!widget.getJsonData().has("chartC")) {
            widget.setJsonData("chartC", randomColor);
        }
    }

    @UIField(order = 1)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldBeanSelection(value = HasTimeValueSeries.class, lazyLoading = true)
    @UIFieldGroup(value = "Chart", order = 10, borderColor = "#9C27B0")
    @UIEditReloadWidget
    default String getChartDataSource() {
        return getJsonData("chartDS");
    }

    default void setChartDataSource(String value) {
        setJsonData("chartDS", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("Chart")
    @UIEditReloadWidget
    default AggregationType getChartAggregationType() {
        return getJsonDataEnum("chartAggrType", AggregationType.Average);
    }

    default void setChartAggregationType(AggregationType value) {
        setJsonData("chartAggrType", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("Chart")
    @UIFieldCodeEditor(autoFormat = true, editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    default String getFinalChartValueConverter() {
        return getJsonData("finValConv", "return value;");
    }

    default void setFinalChartValueConverter(String value) {
        setJsonData("finValConv", value);
    }

    @UIField(order = 1, isRevert = true)
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
    @UIFieldGroup("Chart ui")
    default String getChartLabel() {
        return getJsonData("clbl", "");
    }

    default void setChartLabel(String value) {
        setJsonData("clbl", value);
    }

    default ChartDataset buildTargetDataset(TimeSeriesContext item) {
        HasChartDataSource seriesEntity = item.getSeriesEntity();
        ChartDataset dataset =
                new ChartDataset(item.getId()).setLabel(seriesEntity.getChartLabel());
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(
                    EvaluateDatesAndValues.aggregate(
                            item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    enum ChartType {
        line,
        bar
    }
}

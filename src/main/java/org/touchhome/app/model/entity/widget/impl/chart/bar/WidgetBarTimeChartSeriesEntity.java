package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetBarTimeChartSeriesEntity extends WidgetSeriesEntity<WidgetBarTimeChartEntity>
        implements HasChartDataSource {

    public static final String PREFIX = "wgsbtcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public ChartDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetBarTimeChartSeriesEntity seriesEntity = (WidgetBarTimeChartSeriesEntity) item.getSeriesEntity();
        ChartDataset dataset = new ChartDataset(item.getId());

        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    @Override
    protected void beforePersist() {
        HasChartDataSource.randomColor(this);
    }

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldBeanSelection(HasTimeValueSeries.class)
    @UIFieldGroup(value = "Chart", order = 10, borderColor = "#9C27B0")
    @UIEditReloadWidget
    public String getChartDataSource() {
        return getJsonData("chartDS");
    }
}

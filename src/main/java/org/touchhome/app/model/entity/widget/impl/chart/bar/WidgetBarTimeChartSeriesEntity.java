package org.touchhome.app.model.entity.widget.impl.chart.bar;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.bundle.api.ui.field.UIField;

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
        WidgetBarTimeChartSeriesEntity seriesEntity =
                (WidgetBarTimeChartSeriesEntity) item.getSeriesEntity();
        ChartDataset dataset = new ChartDataset(item.getId());

        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(
                    EvaluateDatesAndValues.aggregate(
                            item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        HasChartDataSource.randomColor(this);
    }

    @UIField(order = 1, required = true)
    public String getChartDataSource() {
        return getJsonData("chartDS");
    }
}

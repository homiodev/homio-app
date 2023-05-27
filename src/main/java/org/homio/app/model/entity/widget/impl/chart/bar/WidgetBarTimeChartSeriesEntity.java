package org.homio.app.model.entity.widget.impl.chart.bar;

import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.homio.app.rest.widget.ChartDataset;
import org.homio.app.rest.widget.EvaluateDatesAndValues;
import org.homio.app.rest.widget.TimeSeriesContext;

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
        ChartDataset dataset = new ChartDataset(item.getId(), ((HasEntityIdentifier) item.getSeriesEntity()).getEntityID());

        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
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
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldBeanSelection(value = HasTimeValueSeries.class, lazyLoading = true)
    @UIFieldGroup(value = "CHART", order = 10, borderColor = "#9C27B0")
    @UIEditReloadWidget
    public String getChartDataSource() {
        return getJsonData("chartDS");
    }
}

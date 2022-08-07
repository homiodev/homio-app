package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;

import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends TimeSeriesChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity,
        WidgetChartsController.TimeSeriesDataset> {

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }

    @Override
    public WidgetChartsController.TimeSeriesDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetLineChartSeriesEntity seriesEntity = (WidgetLineChartSeriesEntity) item.getSeriesEntity();
        WidgetChartsController.TimeSeriesDataset dataset = new WidgetChartsController.TimeSeriesDataset(item.getId(),
                seriesEntity.getTitle(), seriesEntity.getChartColor(), seriesEntity.getChartColorOpacity(),
                seriesEntity.getTension() / 10D, seriesEntity.getStepped().getValue());
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getAggregationType()));
        }
        return dataset;
    }

    @Override
    public boolean fillMissingValues(WidgetSeriesEntity seriesEntity) {
        return ((WidgetLineChartSeriesEntity)seriesEntity).getFillMissingValues();
    }
}
